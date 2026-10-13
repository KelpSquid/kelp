package kelp;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.Function;

/**
 * The lists Kelp downloads that lead to installing something are signed by Samuel: update.json (Kelp's own updates)
 * and the Store's store.json (themes). Next to each list is a .sig file, like update.json.sig: a signature made with
 * Kelp & Squid's private key, which is only on Samuel's laptop. Kelp checks it against the exact bytes of the list
 * before reading anything in it, with the public key below. Someone who got into the GitHub account could change a
 * list, but couldn't make a signature that matches, so their list is never used: no update is offered, and the Store
 * shows nothing. A missing .sig counts the same as a wrong one.
 *
 * Samuel signs with tools/SigningKey.java (Package.java signs update.json by itself). Squid checks the same way, with
 * the same key, in squid.Signatures.
 */
public final class Signatures {
    private Signatures() {
    }

    /** Kelp & Squid's public key: Ed25519, base64 of its X.509 form. Its private half never leaves Samuel's laptop. */
    static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAinGKl0XlKHoYRbob8CJMuPsYWLpm349fNiU15Xsu1/k=";

    /**
     * The key lists have to be signed with. Only the tests change it, to a key of their own; nothing a player can set
     * (a setting, a file, a -D option) does.
     */
    static PublicKey trusted = publicKey(PUBLIC_KEY);

    /** A list that isn't signed by Kelp & Squid: its .sig is missing, or doesn't match it. */
    public static final class Unsigned extends IOException {
        Unsigned(String message) {
            super(message);
        }
    }

    /**
     * Downloads a list and its .sig (the same link with .sig on the end), and gives back the list's bytes only if
     * they're signed by Kelp & Squid. Otherwise it throws {@link Unsigned} with this message, before anything in the
     * list is read.
     */
    static byte[] download(HttpClient client, Function<String, HttpRequest> request, String url, String notSigned)
            throws IOException, InterruptedException {
        HttpResponse<byte[]> list = client.send(request.apply(url), HttpResponse.BodyHandlers.ofByteArray());
        if (list.statusCode() != 200) throw new IOException(url.substring(url.lastIndexOf('/') + 1) + " answered with error " + list.statusCode());
        HttpResponse<byte[]> sig = client.send(request.apply(url + ".sig"), HttpResponse.BodyHandlers.ofByteArray());
        check(list.body(), sig.statusCode() == 200 ? sig.body() : null, notSigned);
        return list.body();
    }

    /** Throws {@link Unsigned} unless sig (a .sig file's contents) is Kelp & Squid's signature of exactly these bytes. */
    static void check(byte[] list, byte[] sig, String notSigned) throws Unsigned {
        if (sig == null || !valid(list, sig)) throw new Unsigned(notSigned);
    }

    static boolean valid(byte[] list, byte[] sig) {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(trusted);
            verifier.update(list);
            return verifier.verify(Base64.getDecoder().decode(new String(sig, StandardCharsets.US_ASCII).strip()));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false; // not base64, or not a signature at all
        }
    }

    static PublicKey publicKey(String base64) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
