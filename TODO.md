# TODO - Known Issues

## Player Error Handling

**Issue**: TV show episodes with HEVC codec show "Ready to play" instead of error message

**Details**:
- When playing TV show episodes that use HEVC/H.265 codec on emulator
- Player detects codec error (format_supported=NO_EXCEEDS_CAPABILITIES)
- ExoPlayer logs show ERROR(7) state with proper error details
- UI displays "Ready to play" instead of the error screen
- Error handling was added to both StreamingPlaybackService and PlaybackViewModel
- Error state is being emitted from service but not properly displayed in UI

**Root Cause**:
- PlaybackState.Error is being created and emitted by the service
- ViewModel's playerListener now has onPlayerError handler
- But UI still shows "Ready to play" instead of error content
- Possible timing issue or state flow collection problem

**To Fix**:
- Debug state flow propagation from service → ViewModel → PlayerScreen
- Check if PlayerScreen's state observation is working correctly
- Verify ErrorContent composable is being triggered
- May need to add logging to trace state changes through the layers

**Test on**: Real Android TV device (NVIDIA Shield, Chromecast) which has proper HEVC support

---

## ~~Stream Info Not Updating on Channel Change~~ ✅ FIXED

**Issue**: Live TV channel switching updates stream but not metadata display

**Status**: ✅ **RESOLVED** - Fixed in commit [pending]

**Solution**:
- Added `currentStreamId` and `currentStreamName` as dependencies to the LaunchedEffect that creates PlayerMetadata
- Changed: `LaunchedEffect(streamUrl)` → `LaunchedEffect(streamUrl, currentStreamId, currentStreamName)`
- This ensures metadata is recreated and sent to the player whenever stream info changes
- Now when channels switch, both video AND metadata update correctly

**Root Cause**:
- The LaunchedEffect that created PlayerMetadata only depended on `streamUrl`
- It used `currentStreamName` inside but didn't track it as a dependency
- When channel switched, `currentStreamName` updated but LaunchedEffect didn't re-run
- Result: Video changed but UI displayed old stream name

**Location Fixed**:
- File: `tv/src/main/java/org/njarasoa/fijerena/feature/player/TvPlayerScreen.kt`
- Line: 221 (LaunchedEffect dependencies)

**Testing**:
- Build: ✅ Successful compilation
- Manual testing: Pending (requires live TV channel switching test)

---

## Testing Notes

Both issues require testing on actual Android TV hardware for proper validation:
- **NVIDIA Shield**: Best HEVC/4K codec support
- **Chromecast with Google TV**: Good general compatibility
- **Sony Bravia**: Test TV-specific behavior

Emulator has limited codec support and may not represent real device behavior accurately.
