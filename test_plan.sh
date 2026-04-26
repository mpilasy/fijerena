#!/bin/bash
# Check if the title is available when resolving playable stream

grep -A 10 -B 10 "resolvePlayableStream" core/network/src/main/java/org/njarasoa/fijerena/core/network/XtreamMediaProvider.kt
