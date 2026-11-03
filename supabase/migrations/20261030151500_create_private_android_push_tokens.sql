-- Migration: 20261030151500_create_private_android_push_tokens.sql
-- Description: Table sécurisée pour stocker les tokens FCM natifs Android
-- Note: Conserve intacte la table push_subscriptions (Web Push) et profiles.fcm_token

CREATE TABLE IF NOT EXISTS public.device_push_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    fcm_token TEXT NOT NULL,
    platform TEXT NOT NULL DEFAULT 'android',
    device_label TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_device_push_token UNIQUE (fcm_token)
);

-- Index pour recherche rapide par utilisateur et par token
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_user_id ON public.device_push_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_fcm_token ON public.device_push_tokens(fcm_token);
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_last_seen ON public.device_push_tokens(last_seen_at);

-- Activer Row Level Security (RLS)
ALTER TABLE public.device_push_tokens ENABLE ROW LEVEL SECURITY;

-- Politiques RLS strictes : chaque utilisateur authentifié gère exclusivement ses propres tokens
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'device_push_tokens' AND policyname = 'Users can view their own push tokens'
    ) THEN
        CREATE POLICY "Users can view their own push tokens"
            ON public.device_push_tokens
            FOR SELECT
            USING (auth.uid() = user_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'device_push_tokens' AND policyname = 'Users can insert their own push tokens'
    ) THEN
        CREATE POLICY "Users can insert their own push tokens"
            ON public.device_push_tokens
            FOR INSERT
            WITH CHECK (auth.uid() = user_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'device_push_tokens' AND policyname = 'Users can update their own push tokens'
    ) THEN
        CREATE POLICY "Users can update their own push tokens"
            ON public.device_push_tokens
            FOR UPDATE
            USING (auth.uid() = user_id)
            WITH CHECK (auth.uid() = user_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'device_push_tokens' AND policyname = 'Users can delete their own push tokens'
    ) THEN
        CREATE POLICY "Users can delete their own push tokens"
            ON public.device_push_tokens
            FOR DELETE
            USING (auth.uid() = user_id);
    END IF;
END
$$;
