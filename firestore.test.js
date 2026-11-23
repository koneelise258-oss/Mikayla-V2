const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const EVE_UID = "eve_intruder_789";
const COUPLE_ID = "couple_alice_bob";
const PAIR_CODE = "MIKA26A";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

function futureDate(msOffset = 60000) {
  return new Date(Date.now() + msOffset);
}

async function seedPairedCouple() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const created = pastDate(20000);
    await db.collection("pairing_codes").doc(PAIR_CODE).set({
      code: PAIR_CODE,
      coupleId: COUPLE_ID,
      creatorUid: ALICE_UID,
      status: "paired",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("couple_spaces").doc(COUPLE_ID).set({
      pairingCode: PAIR_CODE,
      status: "paired",
      partner1Id: ALICE_UID,
      partner2Id: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      partner1Name: "Alice",
      partner2Name: "Bob",
      partner1Avatar: "",
      partner2Avatar: "",
      partner1Bio: "Love",
      partner2Bio: "Always",
      partner1Mood: "💜",
      partner2Mood: "💚",
      partner1LastSeenMs: Date.now() - 5000,
      partner2LastSeenMs: Date.now() - 5000,
      anniversaryDate: Date.now() - 864000000,
      loveQuote: "Toujours ensemble",
      createdAt: created,
      updatedAt: created,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied across all collections", async () => {
  await seedPairedCouple();
  const unauth = testEnv.unauthenticatedContext().firestore();
  const now = pastDate();

  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("pairing_codes").doc(PAIR_CODE).get());
  await assertFails(unauth.collection("couple_spaces").doc(COUPLE_ID).get());
  await assertFails(
    unauth.collection("couple_spaces").doc(COUPLE_ID).collection("messages").get()
  );
  await assertFails(
    unauth.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Unauth",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Users collection: owner-only CRUD, PII isolation, rejects future timestamps and UID spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      bio: "Heureuse",
      moodEmoji: "💜",
      email: "alice@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice M.",
      updatedAt: pastDate(1000),
    })
  );

  // Reject list queries on /users (anti-enumeration)
  await assertFails(aliceDb.collection("users").get());
  // Reject cross-user read or write
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijack",
      updatedAt: pastDate(500),
    })
  );
  // Reject createdAt mutation and future updatedAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc("spoof_doc").set({
      uid: ALICE_UID,
      displayName: "Spoof",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      uid: BOB_UID,
      displayName: "Bob",
      createdAt: futureDate(),
      updatedAt: futureDate(),
    })
  );
});

test("Pairing codes & Couple spaces: create, waiting get, partner2 join, member updates, outsider denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const created = pastDate(10000);

  // Alice creates couple_space + pairing_code
  await assertSucceeds(
    aliceDb.collection("couple_spaces").doc(COUPLE_ID).set({
      pairingCode: PAIR_CODE,
      status: "waiting",
      partner1Id: ALICE_UID,
      partner2Id: "",
      memberUids: [ALICE_UID],
      partner1Name: "Alice",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("pairing_codes").doc(PAIR_CODE).set({
      code: PAIR_CODE,
      coupleId: COUPLE_ID,
      creatorUid: ALICE_UID,
      status: "waiting",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Listing pairing_codes is strictly forbidden (anti-enumeration)
  await assertFails(bobDb.collection("pairing_codes").get());
  // Exact-ID lookup succeeds for signed-in Bob
  await assertSucceeds(bobDb.collection("pairing_codes").doc(PAIR_CODE).get());
  await assertSucceeds(bobDb.collection("couple_spaces").doc(COUPLE_ID).get());

  // Bob joins waiting couple space and marks pairing code as paired
  await assertSucceeds(
    bobDb.collection("couple_spaces").doc(COUPLE_ID).update({
      status: "paired",
      partner2Id: BOB_UID,
      partner2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("pairing_codes").doc(PAIR_CODE).update({
      status: "paired",
      updatedAt: pastDate(1000),
    })
  );

  // Member query matching allow list rule succeeds; unfiltered query fails
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(bobDb.collection("couple_spaces").get());

  // Outsider Eve cannot read paired couple_space or hijack it
  await assertFails(eveDb.collection("couple_spaces").doc(COUPLE_ID).get());
  await assertFails(
    eveDb.collection("couple_spaces").doc(COUPLE_ID).update({
      partner2Id: EVE_UID,
      memberUids: [ALICE_UID, EVE_UID],
      updatedAt: pastDate(500),
    })
  );
  // Reject writing extra forbidden 'id' field when @DocumentId is used
  await assertFails(
    aliceDb.collection("couple_spaces").doc("couple_extra").set({
      id: "couple_extra",
      pairingCode: "MIKA99Z",
      status: "waiting",
      partner1Id: ALICE_UID,
      partner2Id: "",
      memberUids: [ALICE_UID],
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Messages subcollection: member CRUD, query parity, immutable fields, and outsider denial", async () => {
  await seedPairedCouple();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const created = pastDate(5000);
  const messagesCol = aliceDb
    .collection("couple_spaces")
    .doc(COUPLE_ID)
    .collection("messages");

  // Create without redundant 'id' field (matches Kotlin toCreateMap + @DocumentId)
  await assertSucceeds(
    messagesCol.doc("msg_1").set({
      coupleId: COUPLE_ID,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      content: "Coucou mon cœur 💜💚",
      type: "text",
      status: "sent",
      isViewOnce: false,
      isViewed: false,
      isPinned: false,
      isDeletedForEveryone: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Scoped query matching allow list rule succeeds; unfiltered list fails
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertFails(
    bobDb.collection("couple_spaces").doc(COUPLE_ID).collection("messages").get()
  );

  // Bob can mark message read & add reaction
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("messages")
      .doc("msg_1")
      .update({
        status: "read",
        readAtMs: Date.now() - 500,
        reactions: "{\"bob_uid_456\":\"💜\"}",
        updatedAt: pastDate(500),
      })
  );

  // Cannot tamper senderId or createdAt, nor send message with future timestamp or spoofed senderId
  await assertFails(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("messages")
      .doc("msg_1")
      .update({
        senderId: BOB_UID,
        updatedAt: pastDate(400),
      })
  );
  await assertFails(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("messages")
      .doc("msg_spoof")
      .set({
        coupleId: COUPLE_ID,
        senderId: ALICE_UID,
        memberUids: [ALICE_UID, BOB_UID],
        content: "Spoof",
        type: "text",
        status: "sent",
        createdAt: created,
        updatedAt: created,
      })
  );
  await assertFails(
    eveDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("messages")
      .doc("msg_eve")
      .set({
        coupleId: COUPLE_ID,
        senderId: EVE_UID,
        memberUids: [ALICE_UID, BOB_UID],
        content: "Intruder",
        type: "text",
        status: "sent",
        createdAt: created,
        updatedAt: created,
      })
  );
  // Only sender can delete their message
  await assertFails(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("messages")
      .doc("msg_1")
      .delete()
  );
  await assertSucceeds(messagesCol.doc("msg_1").delete());
});

test("Vault, Events, Games & Call Signals subcollections: valid payloads and rule enforcement", async () => {
  await seedPairedCouple();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const created = pastDate(5000);
  const coupleRef = aliceDb.collection("couple_spaces").doc(COUPLE_ID);

  // Vault item
  await assertSucceeds(
    coupleRef.collection("vault_items").doc("vault_1").set({
      coupleId: COUPLE_ID,
      memberUids: [ALICE_UID, BOB_UID],
      addedBy: ALICE_UID,
      addedByName: "Alice",
      title: "Premier dîner",
      type: "note",
      category: "souvenirs",
      caption: "Inoubliable",
      dateAddedMs: Date.now() - 1000,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("vault_items")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  // Invalid vault category denied
  await assertFails(
    coupleRef.collection("vault_items").doc("vault_bad").set({
      coupleId: COUPLE_ID,
      memberUids: [ALICE_UID, BOB_UID],
      addedBy: ALICE_UID,
      title: "Bad",
      type: "note",
      category: "invalid_cat",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Event
  await assertSucceeds(
    coupleRef.collection("events").doc("evt_1").set({
      coupleId: COUPLE_ID,
      memberUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      title: "Anniversaire",
      dateMs: Date.now() + 86400000,
      category: "love",
      note: "Restaurant",
      isRecurring: true,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("events")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Game record
  await assertSucceeds(
    coupleRef.collection("games").doc("game_1").set({
      coupleId: COUPLE_ID,
      memberUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      kind: "coupon",
      payloadJson: "{\"title\":\"Massage 30 min\"}",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("games")
      .doc("game_1")
      .update({
        payloadJson: "{\"title\":\"Massage 30 min\",\"isRedeemed\":true}",
        updatedAt: pastDate(500),
      })
  );
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("games")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Call signal
  await assertSucceeds(
    coupleRef.collection("call_signals").doc("call_1").set({
      coupleId: COUPLE_ID,
      memberUids: [ALICE_UID, BOB_UID],
      callerUid: ALICE_UID,
      callerName: "Alice",
      isVideo: true,
      status: "ringing",
      durationSeconds: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("call_signals")
      .doc("call_1")
      .update({
        status: "accepted",
        updatedAt: pastDate(300),
      })
  );
  await assertFails(
    eveDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("call_signals")
      .doc("call_1")
      .get()
  );

  // Typing & recording indicators on couple_spaces
  await assertSucceeds(
    coupleRef.update({
      partner1TypingAtMs: Date.now() - 100,
      partner1RecordingAtMs: 0,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    eveDb.collection("couple_spaces").doc(COUPLE_ID).update({
      partner1TypingAtMs: Date.now(),
      updatedAt: pastDate(200),
    })
  );

  // Push requests subcollection
  await assertSucceeds(
    coupleRef.collection("push_requests").doc("push_1").set({
      coupleId: COUPLE_ID,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      recipientId: BOB_UID,
      type: "chat_message",
      title: "Mikayala",
      body: "Coucou",
      dataJson: "{\"messageId\":\"msg_1\"}",
      hidePreview: false,
      createdAt: created,
      updatedAt: created,
    })
  );
  // Reject invalid recipient or spoofed senderId or disallowed type
  await assertFails(
    coupleRef.collection("push_requests").doc("push_bad_rec").set({
      coupleId: COUPLE_ID,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      recipientId: EVE_UID,
      type: "chat_message",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    bobDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("push_requests")
      .doc("push_bad_sender")
      .set({
        coupleId: COUPLE_ID,
        memberUids: [ALICE_UID, BOB_UID],
        senderId: ALICE_UID,
        recipientId: BOB_UID,
        type: "chat_message",
        createdAt: created,
        updatedAt: created,
      })
  );
  await assertFails(
    eveDb
      .collection("couple_spaces")
      .doc(COUPLE_ID)
      .collection("push_requests")
      .doc("push_1")
      .get()
  );
});
