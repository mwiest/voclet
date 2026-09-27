# 0001: Connect precomputes its card order with random, bounded gaps

Date: 2025-12-12 (`02dbbe7`, after the simplification in `bc8ae71` on 2025-12-06). Status: accepted.

## Context

Connect must keep enough matchable pairs on screen, yet new cards should not always arrive as a
matching pair, or the game becomes trivial.

## Decision

`generateShuffledCardStack` shuffles the pairs once and places each pair's word2 card 0 to
`minMatchingPairs − 1` pairs after its word1 card (`minMatchingPairs` = two thirds of the grid's
pair capacity, 4 for 14 cards). After a match the next two cards of the sequence are placed.

## Rejected

- A spare-card queue that tracked on-screen pairs (the first implementation, ~100 lines more).
- Pairs always adjacent: every refill would be a matching pair.

## Consequences

The "enough pairs on screen" property is statistical, not guaranteed; `ConnectSequenceTest`
occasionally fails for that reason.
