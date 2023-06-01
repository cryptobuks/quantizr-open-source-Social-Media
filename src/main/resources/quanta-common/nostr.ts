import {
  Event
} from "nostr-tools";

export interface NostrEvent {
  id: string;
  sig: string;
  pubkey: string;
  kind: number;
  content: string;
  tags: string[][];
  createdAt: number;
}

export interface NostrEventWrapper {
  event: NostrEvent;
  nodeId: string;
  npub: string;
  relays: string;
}

export const makeEvent = (event: NostrEvent): Event => {
  return {
    id: event.id,
    sig: event.sig,
    pubkey: event.pubkey,
    kind: event.kind,
    content: event.content,
    tags: event.tags,
    created_at: event.createdAt
  };
}

export const makeNostrEvent = (event: Event): NostrEvent => {
  return {
    id: event.id,
    sig: event.sig,
    pubkey: event.pubkey,
    kind: event.kind,
    content: event.content,
    tags: event.tags,
    createdAt: event.created_at
  };
}

