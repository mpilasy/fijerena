# Fijerena User Guide

This guide shows how to use Fijerena on an Android TV and on a phone. For every detail, see the
[feature reference](FEATURES.md).

- [Install](#install)
- [Add Your First Source](#add-your-first-source)
- [Find Your Way Around](#find-your-way-around)
- [Live TV and the Guide](#live-tv-and-the-guide)
- [Films and TV Shows](#films-and-tv-shows)
- [Search](#search)
- [The Player](#the-player)
- [Profiles](#profiles)
- [Sports](#sports)
- [Live Sync and Send to TV](#live-sync-and-send-to-tv)
- [Back Up and Move to a New Device](#back-up-and-move-to-a-new-device)
- [Settings in Short](#settings-in-short)
- [Problems and Answers](#problems-and-answers)

## Install

There are two apps: the **TV app** for a TV or TV box, and the **phone app** for a phone. They are
separate, so you can have both on one device if you want.

- **With Obtainium** (it tells you when there is an update): add the address
  `https://github.com/mpilasy/fijerena`, with the APK filter `fijerena-phone` for the phone app or
  `fijerena-tv` for the TV app. The badges in the [README](../README.md#get-the-app) do this for you
  on a phone.
- **From GitHub:** download the APK from the
  [latest release](https://github.com/mpilasy/fijerena/releases/latest) and open it.

To pass the app to another person, open Settings → About → About Fijerena → Get the App: each
badge shows a QR code to scan with a phone.

You need Android 11 or later. See the README for the [devices it runs on](../README.md#will-it-run-on-my-device).

## Add Your First Source

Fijerena has no content of its own. It plays the sources that you add:

| Source type | What it gives you | What you need |
|---|---|---|
| **Xtream IPTV** | Live TV, films, TV shows and the guide | The server address, user name and password from your provider |
| **Jellyfin** | Films and TV shows, with your progress kept on the server | Your server's address and a login (or Quick Connect) |
| **Remote M3U** | Live TV, and the video files in the playlist | The playlist's link |

1. Open the app. The first time, it opens **Add Source**.
2. Choose the source type and fill in the fields.
3. Save. The app first checks the login. If the check fails, it tells you why in plain words, and you
   can go back to fix it.
4. The first load can take a few minutes for a large catalogue and guide. Home says "Still loading
   your library…" until it is ready.

You can add more sources later: **Settings → Sources & Guide → Manage Sources**. Home, Search and
the lists show the content of all the sources of your profile together.

**Jellyfin Quick Connect:** choose **Use Quick Connect** instead of a password. The app shows a
6-digit code. Approve it in Jellyfin's web page or in another Jellyfin app.

## Find Your Way Around

- **TV:** use the arrow keys, **OK** and **Back** on your remote. The menu on the left opens Home,
  Live TV, Films, TV Shows, Sports, Search, your profile and Settings.
- **Phone:** the bar at the bottom opens Home, Live TV, Films, TV Shows and Sports. Your profile
  picture at the top opens the profile menu and Settings.

**Home** shows what you were watching, your favourite and recent channels, the next games of the
teams you follow, and new episodes.

## Live TV and the Guide

- **TV:** select a channel and it plays beside the list. Press **OK** on the channel that plays to
  go full screen. In full screen, **Up** and **Down** change the channel, and **Left** or **Right**
  open the channel list over the video.
- **Phone:** tap a channel and it plays in a small player above the list. Tap the small player for
  full screen. In full screen, swipe up or down to change the channel.
- **The TV guide** shows what is on now and next for each channel. **Search the guide** finds a
  programme by its name. You can set a reminder for a programme that has not started.
- **Favourites:** add a channel to your favourites from the player's controls. Each profile has its
  own favourites.

If a channel does not play, the app says "<channel> is not available right now" and offers the next
channel.

## Films and TV Shows

- Open a film or a show to see its details, cast and trailer.
- Fijerena remembers where you stopped. **Continue Watching** on Home brings you back, and **Up
  Next** plays the next episode.
- You can mark a film or an episode as watched or not watched.
- **Wishlist:** keep a list of films and shows you want to watch, also titles that your sources do
  not have yet (this needs a TMDB key). Home tells you when one of them comes to a source.
- **New Episodes:** Home tells you when a source adds an episode of a show that you watched in the
  last 60 days or that is a favourite (Xtream sources).
- **More details and posters from TMDB:** enter your own free TMDB key in **Settings → Sources &
  Guide → Film and Show Details (TMDB)**. Without a key, the app sends nothing to TMDB.

## Search

Search finds channels, films and shows in all your sources. Type 2 letters or more.

- Each word finds the start of a word in the name: "har pot" finds Harry Potter.
- Put a word in quotes to find the whole word only: `"la" land` finds La La Land.
- Use the filter to narrow the results by decade, genre, language or region.
- The **Teams** type finds sports teams and leagues to follow.

## The Player

| | TV remote | Phone |
|---|---|---|
| Show the controls | **OK** | Tap |
| Pause and play | **Play/Pause** key, or the Play button in the controls | The play button |
| Jump 10 seconds | **Left** or **Right** (films and shows) | Double-tap the left or right side |
| Move far in a film | Hold **Left** or **Right** | The seek bar |
| Change the channel | **Up** or **Down** | Swipe up or down |
| Leave | **Back** | Back |

The controls also have **Audio**, **Subtitles** and **Quality**. The app remembers your choices
for each film and each series. **Night Audio** (in Audio) makes quiet voices louder and loud
sounds quieter.

## Profiles

Each person can have a profile with their own favourites, history, look and filters.

- **Add a profile:** Settings → Profiles → Add Profile.
- **Switch profile:** on TV, the profile in the left menu; on the phone, your profile picture at the
  top.
- **PIN:** any profile can have a 4-digit PIN. The PIN of an admin profile protects the admin
  settings.
- **Admin:** an admin adds sources and changes the settings of the whole household. There is always
  at least one admin.
- **Child profile:** a child profile has no Settings and cannot add sources. On TV, it has a simple
  Home for children.
- **Parental controls:** Settings → Parental Controls. Hide categories and titles by word, for each
  profile.

## Sports

Follow teams and leagues to see their games and the channels of your sources that show them.

1. Open Search, choose the **Teams** type, and type a team or a league ("Arsenal", "NBA").
2. Open it and select **Follow**.
3. Its games now show in the **Sports** section and on Home. On the phone, a **Sports** tab comes
   when you follow a team.

- A team's page shows its results of the last 30 days and its next games, with the score while a
  game is on.
- Each game shows the best channel of your sources that shows it. Select the game to see all of
  them, and to play one.
- **Reminders:** select the bell of a game to get a notification before it starts (10 minutes
  before by default; change it in Settings → This Device → Reminder Time). The bell on a team's
  page sets a reminder for every game of the team.
- On the phone, pull the Sports page down to read the games again.

The games come from ESPN, the NHL and MLB. An admin can turn Sports off in Settings → Sources &
Guide.

## Live Sync and Send to TV

Live sync keeps your profiles, sources, favourites and progress the same on all your devices. It
is optional, and it needs a sync server that you host yourself (see [server/README.md](../server/README.md)).
The Fijerena project does not run a sync server and never gets your sync data. Everything is
encrypted on your device before it is sent. The server cannot read it or tell who it belongs to.

1. On one device: **Settings → Sync & Backup → Live Sync**, enter your server's address.
2. To add a phone: scan the invitation QR code that a device of the group shows.
3. To add a TV: the TV shows a code; scan it with a phone of the group.

Changes reach the other open devices in a few seconds. A closed app catches up when you open it.

**Move to another server:** to change servers, update the app on every device of the group and
update the new server. Then, on one device, select **Move to another server** on the Live Sync
page, type the new address, select **Check server**, then **Move**. Keep the device on until the
copy ends. The other devices move by themselves the next time you open them. When all of them
have moved, select **Purge old server** to delete the group's data from the old server.
To stop a move that does not finish (for example, the new server stops), select **Cancel move**.
The device then syncs with the old server again. You can cancel only before the other devices
can see the move; after that, the move must finish.

**Send to TV:** on the phone, select **Send to TV** on a film, an episode or a channel, and choose
a TV of your sync group. The TV plays it. Fijerena must be open on the TV.

## Back Up and Move to a New Device

The app does not use Android's cloud backup. To move to a new device:

- **Settings → Sync & Backup → Back Up to a File** on the old device, then **Restore from a File**
  on the new one; or
- add the new device to your live sync group.

A backup file holds your sources and their logins. Keep it private.

## Settings in Short

- **Your Profile:** your name, colour, PIN, look (theme and style), and playback choices (audio and
  subtitle language, play the next episode automatically).
- **Profiles** (admin): add, change and remove profiles.
- **Parental Controls** (admin): word lists and content filters for each profile.
- **This Device** (admin): language (English, French or Malagasy), text size on TV, reminder time.
- **Sources & Guide** (admin): sources, guide sources, TMDB key, Sports on or off.
- **Sync & Backup** (admin): live sync, back up and restore.
- **About:** the version, the licences, device information and the logs.

## Problems and Answers

**The app does not install.**
Your device needs Android 11 or later. Many low-cost TV boxes and older Fire TV devices have an
older Android.

**"Connection Failed" when I save a source.**
Check the address, the user name and the password with your provider. The message says what went
wrong: the login is refused, nothing answers at the address, or the link is not an M3U playlist.

**A channel does not play.**
Try the next channel. If no channel plays, check that your subscription is still active (Settings
→ Sources & Guide → Manage Sources shows when it expires) and that your connection works.

**The guide is empty.**
A large guide can take some minutes to load the first time. Check the guide sources in Settings →
Sources & Guide.

**A game says "Not found on your channels".**
None of your channels shows that game in its name or in the guide. Sports finds a game only on the
channels of your own sources.

**Something else is wrong.**
Settings → About → Logs shows what went wrong. You can share the log (logins are hidden) when you
[open an issue](https://github.com/mpilasy/fijerena/issues).
