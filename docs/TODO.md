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
- Debug state flow propagation from service -> ViewModel -> PlayerScreen
- Check if PlayerScreen's state observation is working correctly
- Verify ErrorContent composable is being triggered
- May need to add logging to trace state changes through the layers

**Test on**: Real Android TV device (NVIDIA Shield, Chromecast) which has proper HEVC support

---

## ~~Stream Info Not Updating on Channel Change~~ FIXED

**Status**: RESOLVED

---

## ~~Login Screen Flash on Mobile~~ FIXED

**Status**: RESOLVED - Login screen removed entirely from mobile navigation in Phase 5. Both TV and mobile now use Settings-based provider configuration with auto-session restore.

---

## ~~Mobile Live TV Playback Failures~~ FIXED

**Status**: RESOLVED - Added `setContentType()` call to MobilePlayerScreen. Without this, Live TV streams used VOD buffer settings (15s min buffer) causing timeouts.

---

## Testing Notes

Both issues require testing on actual Android TV hardware for proper validation:
- **NVIDIA Shield**: Best HEVC/4K codec support
- **Chromecast with Google TV**: Good general compatibility
- **Sony Bravia**: Test TV-specific behavior

Emulator has limited codec support and may not represent real device behavior accurately.

## Recently Completed (Phase 5)

- User-selectable themes (4 dark variants)
- Multiple provider management (Room database)
- Login screen removal (both TV and mobile)
- Mobile player buffer profile fix
- Provider migration from legacy single-provider storage
