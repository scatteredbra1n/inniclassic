package com.themoon.y1.apple;

/** The phone-side pairing page. Apple's login runs in the browser through MusicKit JS. */
public final class AppleMusicPage {
    public static final String PATH = "/apple-music";

    private AppleMusicPage() {}

    public static String html(String developerToken) {
        if (!AppleMusicConfig.isUsable(developerToken)) return notConfigured(developerToken);
        return configured(developerToken.trim());
    }

    private static String notConfigured(String rawToken) {
        String reason = rawToken != null && !rawToken.trim().isEmpty()
                ? "A developer token was found, but it is not a MusicKit JWT (three segments separated by dots)."
                : "No MusicKit developer token is set on this iPod.";
        return page("Apple Music is not configured",
                "<p>" + reason + " Nothing has been added to your library.</p>" +
                "<ol>" +
                "<li>In your Apple Developer account, create a MusicKit key and note the team ID and key ID.</li>" +
                "<li>Sign a developer token JWT with that key (ES256, <code>iss</code> = team ID, <code>kid</code> = key ID, expiry at most 6 months). Keep the <code>.p8</code> private key off this device.</li>" +
                "<li>Put the JWT in <code>local.properties</code> as <code>musickit.developer.token=...</code> and rebuild, or save just the JWT as <code>/storage/sdcard0/.apple_music_developer_token</code> and reopen this page.</li>" +
                "</ol>" +
                "<p>When a token is present, this page uses Apple's MusicKit JS so you can sign in on this phone. The iPod does not show Apple's login.</p>",
                "");
    }

    private static String configured(String token) {
        String jsToken = escapeJs(token);
        String body = "<p>This page is served by your iPod on the local network. Sign in with Apple here — the iPod will not open Apple's login itself.</p>" +
                "<ol>" +
                "<li>Tap <b>Sign in with Apple Music</b>. Apple's MusicKit window asks for your Apple ID.</li>" +
                "<li>After you allow access, this page sends only your music-user token back to the iPod.</li>" +
                "<li>The iPod downloads your Apple Music library and mixes those songs in with the files already on the device.</li>" +
                "</ol>" +
                "<button id='signIn' type='button'>Sign in with Apple Music</button>" +
                "<button id='playHere' type='button' class='secondary' disabled>Play in this browser</button>" +
                "<p id='status'>Waiting for you to sign in.</p>" +
                "<p class='note'>Songs show up on the iPod with an Apple Music icon. This player cannot stream full Apple Music tracks — use <b>Play in this browser</b> after signing in. Local files keep playing on the iPod as before.</p>" +
                "<script src='https://js-cdn.music.apple.com/musickit/v3/musickit.js' data-web-components async></script>" +
                "<script>" +
                "const developerToken = '" + jsToken + "';" +
                "let music = null;" +
                "function setStatus(text) { document.getElementById('status').textContent = text; }" +
                "document.addEventListener('musickitloaded', async function () {" +
                "  try {" +
                "    await MusicKit.configure({ developerToken: developerToken, app: { name: 'InniClassic', build: '1.4.5' } });" +
                "    music = MusicKit.getInstance();" +
                "  } catch (err) {" +
                "    setStatus('MusicKit could not start. Check that the developer token is valid. ' + (err && err.message ? err.message : ''));" +
                "  }" +
                "});" +
                "document.getElementById('signIn').onclick = async function () {" +
                "  if (!music) { setStatus('MusicKit is not ready yet. Reload this page if it stays that way.'); return; }" +
                "  try {" +
                "    await music.authorize();" +
                "    const userToken = music.musicUserToken;" +
                "    if (!userToken) { setStatus('Apple did not return a music-user token.'); return; }" +
                "    setStatus('Signed in. Sending the token to the iPod...');" +
                "    const response = await fetch('/api/apple-music/session', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'musicUserToken=' + encodeURIComponent(userToken) });" +
                "    const payload = await response.json();" +
                "    if (!payload.ok) { setStatus(payload.error || 'The iPod rejected the token.'); return; }" +
                "    document.getElementById('playHere').disabled = false;" +
                "    poll();" +
                "  } catch (err) {" +
                "    setStatus('Apple login was cancelled or failed. ' + (err && err.message ? err.message : ''));" +
                "  }" +
                "};" +
                "async function poll() {" +
                "  const response = await fetch('/api/apple-music/status');" +
                "  const status = await response.json();" +
                "  if (status.syncing) { setStatus('Signed in. The iPod is loading your Apple Music library...'); setTimeout(poll, 1500); return; }" +
                "  if (status.error) { setStatus(status.error); return; }" +
                "  if (status.paired) { setStatus('Done. ' + status.songCount + ' Apple Music songs are in the iPod library.'); return; }" +
                "  setStatus('Waiting for the iPod.');" +
                "}" +
                "document.getElementById('playHere').onclick = async function () {" +
                "  if (!music) return;" +
                "  try {" +
                "    const result = await music.api.music('/v1/me/library/songs', { limit: 25 });" +
                "    const rows = result && result.data && result.data.data ? result.data.data : [];" +
                "    if (!rows.length) { setStatus('Apple Music returned no songs to play in this browser.'); return; }" +
                "    await music.setQueue({ songs: rows.map(function (row) { return row.id; }) });" +
                "    await music.play();" +
                "    setStatus('Playing in this browser. The iPod still lists these songs, but it does not stream them.');" +
                "  } catch (err) {" +
                "    setStatus('This browser could not start Apple Music playback. ' + (err && err.message ? err.message : String(err)));" +
                "  }" +
                "};" +
                "</script>";
        return page("Pair Apple Music", body, "");
    }

    private static String page(String heading, String body, String extra) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "<title>" + heading + "</title><style>" +
                "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#111;color:#f2f2f2;margin:0;padding:24px;}" +
                "main{max-width:560px;margin:0 auto;}" +
                "h1{font-size:28px;margin:0 0 12px;}" +
                "p,li{font-size:17px;line-height:1.4;}" +
                "code{color:#ffb4b8;}" +
                "button{display:block;width:100%;font-size:18px;padding:14px 16px;margin:10px 0;border:0;border-radius:12px;background:#fc3c44;color:white;font-weight:600;}" +
                "button.secondary{background:#2a2a2e;color:#fff;}" +
                "button:disabled{opacity:0.45;}" +
                ".note{color:#b5b5b8;font-size:15px;}" +
                "</style></head><body><main><h1>" + heading + "</h1>" + body + extra + "</main></body></html>";
    }

    private static String escapeJs(String token) {
        return token.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\u003c");
    }
}
