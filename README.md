# TV Hud — ad-free home screen for your Coocaa Google TV

TV Hud replaces the Google TV home screen with a fast, clean, ad-free one.

**What's in this version (1.0)**

- **Home:** big clock, date and live weather over a night-sky backdrop. Your apps show their official logos, and your HDMI consoles have rename-able cards (e.g. "PS5").
- **All apps:** search, categories (Streaming, Music, Games, Tools), hide apps, add or remove apps on Home, uninstall.
- **Ask AI:** a built-in assistant powered by Claude. Ask by voice or by typing, and say things like "open Netflix".
- **Play from PC:** your recently played Steam games, a Wake-on-LAN button to switch your PC on, and controller status. Play opens Steam Link.
- **Ad-block browser:** blocks ads, trackers and pop-ups. Has zoom, a desktop-site switch and full-screen video.
- **Quick settings:** Wi-Fi, Bluetooth and controllers, picture, sound, screensaver, weather city, and "Free up memory".
- **Ambient screensaver:** a big clock, date and weather instead of ads.

**Coming in phase 2:** Watch together (synced countdown) and Stream to Discord. Both need a small online service and a PC companion program.

---

## Step 1 — Get the APK (the installable file)

### Option A: let GitHub build it for you (free, no software needed)

1. Create a free account at **github.com** and click **New repository**.
   - Name it `tvhud` and choose **Public**. GitHub's free builds are unlimited on public repositories.
2. Click **uploading an existing file**. Drag in **everything inside this folder**, including the hidden `.github` folder, and click **Commit changes**.
   - If your computer hides the `.github` folder, unzip the project again and look for "show hidden files".
3. Open the **Actions** tab. A build called "Build TV Hud APK" starts automatically and takes about 5–8 minutes.
4. When it shows a green tick, open **Releases** (on the right of the repository page) and find **TV Hud (latest build)**. The file is **TVHud.apk**.

Your direct download link will be:

```
https://github.com/YOUR-USERNAME/tvhud/releases/download/latest/TVHud.apk
```

### Option B: Android Studio

Open this folder in Android Studio and pick **Build › Build APK(s)**.

---

## Step 2 — Install it on the TV

1. On the TV, open the Google Play Store and install **Downloader** (by AFTVnews).
2. Allow Downloader to install apps:
   - Go to **Settings › System › About** and click **Android TV OS build** 7 times to unlock Developer options.
   - Then go to **Settings › Apps › Security & restrictions › Unknown sources** and turn on **Downloader**.
3. Open Downloader, type your download link from Step 1, and choose **Install**.
4. TV Hud now appears in your apps list.

---

## Step 3 — Make TV Hud your home screen (so the Home button opens it)

Google TV always sends the Home button to its own launcher. There are two ways around that.

### Easiest: Button Mapper (no computer)

1. Install **Button Mapper** from the Play Store.
2. Map **Home** → **Launch app** → **TV Hud**.
3. Follow its prompt to turn on its accessibility permission.

### Cleanest: switch off the Google TV home screen with a computer (fully undoable)

1. On the TV, go to **Settings › System › Developer options** and turn on **USB debugging** (on some TVs it's called **Network debugging**).
2. Note the TV's IP address from **Settings › Network & Internet › your Wi-Fi**.
3. On your PC, download **Android SDK Platform-Tools** from developer.android.com and open a command window in that folder.
4. Run:

```
adb connect TV-IP-ADDRESS
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

5. Accept the prompt that appears on the TV.
6. Press Home on the remote, choose **TV Hud**, then **Always**.

**To undo it at any time:**

```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
```

Notes:

- Some Google TV extras tied to its home screen, such as profiles, won't work while it's switched off. Netflix, YouTube, casting and the voice remote keep working.
- A big system update can switch the Google TV home screen back on. Just run the commands again.

---

## Step 4 — Set up the extras (all optional)

| Feature | Where | What you need |
|---|---|---|
| Weather city | Settings (gear) › Weather city | Just the city name. Weather is free from Open-Meteo. |
| Ask AI | Home › Ask AI › API key | An Anthropic API key from **console.anthropic.com**. Each question costs a fraction of a cent on your own account. |
| Steam games list | Play from PC › "Show your Steam games here" | Your Steam ID and a free key from **steamcommunity.com/dev/apikey**. Steam privacy must have "Game details" set to Public. |
| Wake PC | Play from PC › Set up | Your PC's MAC address (`getmac /v` on Windows), with Wake-on-LAN turned on in the BIOS. Works best when the PC uses an Ethernet cable. |
| Playing PC games | Play from PC › Play | **Steam Link** (free on Play Store) on the TV, Steam open on the PC, and a Bluetooth controller. |
| Screensaver | Settings › Screensaver | Choose **TV Hud Ambient**. |

## Remote controls

- **OK:** open
- **Hold OK or press Menu:** options (move, hide, rename, uninstall)
- **Back:** return to Home

## Limits to know

- **Picture modes, sleep timer and turning the TV off** are controlled by Coocaa's own system. TV Hud opens those settings pages, but it can't change them itself.
- **The ad blocker** works on websites in the TV Hud browser only. Ads inside other apps, such as the YouTube app, aren't affected.
- **Netflix, Prime Video, Disney+ and HBO Max** can't be streamed to Discord or captured. Those apps block it.
