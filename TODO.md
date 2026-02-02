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

## Stream Info Not Updating on Channel Change

**Issue**: Live TV channel switching updates stream but not metadata display

**Details**:
- When using D-pad up/down to change channels in live TV player
- Stream switches correctly (video changes)
- Stream name/title on screen does not update to show new channel info
- Only happens during channel switching, not initial load

**Expected Behavior**:
- When channel changes, both video AND metadata should update
- Display should show new channel name/title

**Location**:
- File: `tv/src/main/java/org/njarasoa/fijerena/ui/player/PlayerScreen.kt`
- Function: Channel switching logic (onNextChannel/onPreviousChannel)
- Related: `TvPlayerScreen.kt` - switchToNextChannel() / switchToPreviousChannel()

**To Fix**:
- Update currentMetadata when switching channels
- Ensure stream name updates trigger UI recomposition
- May need to update PlayerMetadata state in ViewModel when switching

---

## Testing Notes

Both issues require testing on actual Android TV hardware for proper validation:
- **NVIDIA Shield**: Best HEVC/4K codec support
- **Chromecast with Google TV**: Good general compatibility
- **Sony Bravia**: Test TV-specific behavior

Emulator has limited codec support and may not represent real device behavior accurately.
