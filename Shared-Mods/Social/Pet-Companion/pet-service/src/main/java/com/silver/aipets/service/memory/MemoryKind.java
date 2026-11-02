package com.silver.aipets.service.memory;

/** Distinct grounded memory roles; consolidated summaries retain their source episodes. */
public enum MemoryKind { FACT, EPISODE, PREFERENCE, RELATIONSHIP_EVENT, OPEN_THREAD, CORRECTION, SUMMARY }
