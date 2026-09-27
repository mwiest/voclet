# 0026: The user marks the part of the photo to scan before reading

Date: 2026-09-23. Status: accepted.

## Context

Photos include headings, a facing page, or a page shot at an angle.

## Decision

After capture, four draggable corners (`PageSelector`, starting 5 % inside the photo) select a
convex quad. Scan flattens it with a perspective warp (`warpedTo`). Both backends receive the
warped bitmap.

## Rejected

Automatic page-edge detection: never evaluated.

## Consequences

The page is deskewed before detection. The quad logic is pure and JVM-tested
(`PageSelectionTest`). A thin selection can reach the detector's aspect-ratio blow-up (see the
leftovers).
