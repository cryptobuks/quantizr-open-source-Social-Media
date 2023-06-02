import {
  Event
} from "nostr-tools";
import { NostrEvent } from "./JavaIntf";

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
