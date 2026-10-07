package kelp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Signs in with a Microsoft account, the same way the official launcher does:
 * Microsoft gives a short code, you type it at microsoft.com/link, then Kelp trades that sign-in
 * for an Xbox token, then a Minecraft token, then your Minecraft name and skin ID.
 *
 * Kelp never sees your password: you type it on Microsoft's own page.
 *
 * Microsoft only lets a launcher do this with a "client ID" that Mojang has approved. Until Kelp has one,
 * {@link #ready()} is false and Kelp says sign-in isn't ready yet.
 */
public final class MicrosoftLogin {
    /** Kelp's approved app ID from Azure. Empty until Mojang approves Kelp; -Dkelp.clientId sets it for testing. */
    public static final String CLIENT_ID = System.getProperty("kelp.clientId", "");

    private static final String SCOPE = "XboxLive.signin offline_access";

    /** Where each step happens. Tests point these at a pretend server. */
    public record Servers(String microsoft, String xbox, String xsts, String minecraft) {
        public static final Servers REAL = new Servers("https://login.microsoftonline.com/consumers/oauth2/v2.0",
                "https://user.auth.xboxlive.com", "https://xsts.auth.xboxlive.com", "https://api.minecraftservices.com");
    }

    /**
     * The code to type at Microsoft's page.
     *
     * @param page      where to type it, usually https://www.microsoft.com/link
     * @param expiresAt when the code stops working, in milliseconds since 1970
     */
    public record Code(String userCode, String page, String deviceCode, int intervalSeconds, long expiresAt) {
    }

    /** A sign-in problem, explained for people. signInAgain is true when the saved sign-in stopped working. */
    public static class SignInException extends IOException {
        public final boolean signInAgain;

        public SignInException(String message, boolean signInAgain) {
            super(message);
            this.signInAgain = signInAgain;
        }
    }

    private final String clientId;
    private final Servers servers;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public MicrosoftLogin() {
        this(CLIENT_ID, Servers.REAL);
    }

    MicrosoftLogin(String clientId, Servers servers) {
        this.clientId = clientId;
        this.servers = servers;
    }

    /** Whether Kelp has an approved client ID, so signing in can work at all. */
    public static boolean ready() {
        return !CLIENT_ID.isBlank();
    }

    /** Step 1: asks Microsoft for a code to show you. */
    public Code start() throws IOException, InterruptedException {
        Map<String, Object> json = form(servers.microsoft() + "/devicecode", Map.of("client_id", clientId, "scope", SCOPE));
        if (json.get("user_code") == null) throw new SignInException("Microsoft didn't give a code. " + why(json), false);
        int interval = json.get("interval") instanceof Double d ? d.intValue() : 5;
        long expiresIn = json.get("expires_in") instanceof Double d ? d.longValue() : 900;
        return new Code((String) json.get("user_code"), (String) json.get("verification_uri"), (String) json.get("device_code"),
                interval, System.currentTimeMillis() + expiresIn * 1000);
    }

    /**
     * Step 2: waits while you type the code and sign in, then finishes signing in to Minecraft.
     * Checks cancelled now and then, and returns null if it's true.
     */
    public Account waitForSignIn(Code code, BooleanSupplier cancelled) throws IOException, InterruptedException {
        int interval = code.intervalSeconds();
        while (true) {
            for (int i = 0; i < interval * 10; i++) { // sleep in small steps so Cancel works right away
                if (cancelled.getAsBoolean()) return null;
                Thread.sleep(100);
            }
            if (cancelled.getAsBoolean()) return null;
            if (System.currentTimeMillis() > code.expiresAt()) throw new SignInException("The code ran out of time. Try again!", false);
            Map<String, Object> json = form(servers.microsoft() + "/token", Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", code.deviceCode()));
            String error = (String) json.get("error");
            if (error == null) return finish((String) json.get("access_token"), (String) json.get("refresh_token"));
            switch (error) {
                case "authorization_pending" -> { } // not signed in yet, keep waiting
                case "slow_down" -> interval += 5; // Microsoft asked Kelp to check less often
                case "expired_token" -> throw new SignInException("The code ran out of time. Try again!", false);
                case "authorization_declined" -> throw new SignInException("Sign-in was cancelled on Microsoft's page.", false);
                default -> throw new SignInException("Microsoft said no. " + why(json), false);
            }
        }
    }

    /** Signs in again with the saved refresh token, for a new Minecraft token. Your password isn't needed. */
    public Account refresh(Account account) throws IOException, InterruptedException {
        Map<String, Object> json = form(servers.microsoft() + "/token", Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "scope", SCOPE,
                "refresh_token", account.refreshToken() == null ? "" : account.refreshToken()));
        if (json.get("access_token") == null) {
            // Usually the password changed, or the account removed Kelp's access
            throw new SignInException("Your sign-in for " + account.name() + " stopped working. Sign in again in Options > Accounts.", true);
        }
        return finish((String) json.get("access_token"), (String) json.get("refresh_token"));
    }

    /** Steps 3 to 6: Microsoft token, then Xbox, then Xbox's OK for Minecraft, then Minecraft itself, then your profile. */
    private Account finish(String microsoftToken, String refreshToken) throws IOException, InterruptedException {
        Map<String, Object> xbox = json(servers.xbox() + "/user/authenticate", "{\"Properties\": {\"AuthMethod\": \"RPS\", "
                + "\"SiteName\": \"user.auth.xboxlive.com\", \"RpsTicket\": " + quote("d=" + microsoftToken) + "}, "
                + "\"RelyingParty\": \"http://auth.xboxlive.com\", \"TokenType\": \"JWT\"}", null);
        String xboxToken = (String) xbox.get("Token");
        if (xboxToken == null) throw new SignInException("Xbox couldn't sign you in. " + why(xbox), false);

        Map<String, Object> xsts = json(servers.xsts() + "/xsts/authorize", "{\"Properties\": {\"SandboxId\": \"RETAIL\", "
                + "\"UserTokens\": [" + quote(xboxToken) + "]}, \"RelyingParty\": \"rp://api.minecraftservices.com/\", "
                + "\"TokenType\": \"JWT\"}", null);
        if (xsts.get("XErr") instanceof Double code) throw new SignInException(xboxProblem(code.longValue()), false);
        String xstsToken = (String) xsts.get("Token");
        if (xstsToken == null) throw new SignInException("Xbox couldn't sign you in. " + why(xsts), false);
        List<Object> claims = Json.array(Json.object(xsts.get("DisplayClaims")).get("xui"));
        String userHash = (String) Json.object(claims.get(0)).get("uhs");

        Map<String, Object> minecraft = json(servers.minecraft() + "/authentication/login_with_xbox",
                "{\"identityToken\": " + quote("XBL3.0 x=" + userHash + ";" + xstsToken) + "}", null);
        String minecraftToken = (String) minecraft.get("access_token");
        if (minecraftToken == null) {
            // Minecraft says no to launchers Mojang hasn't approved
            throw new SignInException("Minecraft didn't accept Kelp's sign-in. " + why(minecraft), false);
        }
        long expiresIn = minecraft.get("expires_in") instanceof Double d ? d.longValue() : 86400;

        Map<String, Object> profile = get(servers.minecraft() + "/minecraft/profile", minecraftToken);
        if (profile.get("id") == null) {
            throw new SignInException("This Microsoft account doesn't own Minecraft: Java Edition yet, "
                    + "or hasn't picked a Minecraft name. Try it in the official launcher first.", false);
        }
        return new Account((String) profile.get("id"), (String) profile.get("name"), true, refreshToken, minecraftToken,
                System.currentTimeMillis() + expiresIn * 1000);
    }

    /** Xbox's numbered problems, in plain words. Kids' accounts need a grown-up's OK (code ...238). */
    static String xboxProblem(long code) {
        if (code == 2148916233L) return "This Microsoft account has no Xbox profile yet. Sign in once at xbox.com, then try again.";
        if (code == 2148916235L) return "Xbox isn't available in your country, so Minecraft can't sign in.";
        if (code == 2148916236L || code == 2148916237L) return "This account needs its age checked at xbox.com before it can play.";
        if (code == 2148916238L) {
            return "This is a kid's account. A grown-up needs to add it to their Microsoft Family (family.microsoft.com), then try again.";
        }
        return "Xbox said no (problem " + code + ").";
    }

    /** Microsoft's own explanation, if it sent one. */
    private static String why(Map<String, Object> json) {
        for (String key : new String[] {"error_description", "errorMessage", "error", "Message"}) {
            if (json.get(key) instanceof String text && !text.isBlank()) {
                return text.length() > 200 ? text.substring(0, 200) + "..." : text;
            }
        }
        return "";
    }

    private Map<String, Object> form(String url, Map<String, String> fields) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            if (!body.isEmpty()) body.append('&');
            body.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
        }
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())));
    }

    private Map<String, Object> json(String url, String body, String token) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request);
    }

    private Map<String, Object> get(String url, String token) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token).GET());
    }

    /** Sends a request and reads the JSON that comes back, even with an error code (errors are explained in it). */
    private Map<String, Object> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = client.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SignInException("Couldn't sign in: " + Downloader.explain(e), false);
        }
        String body = response.body().isBlank() ? "{}" : response.body();
        try {
            Map<String, Object> json = Json.object(Json.parse(body));
            return json != null ? json : Map.of();
        } catch (RuntimeException notJson) {
            return Map.of("error", "got error " + response.statusCode());
        }
    }

    /** Text in JSON quotes. Tokens only use plain characters, but quotes and backslashes are escaped to be safe. */
    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
