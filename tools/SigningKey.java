import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Kelp & Squid's signing key. The lists that lead to installing something (Kelp's update.json, the Store's store.json
 * and official-capes.json) are signed with it, and Kelp and Squid only use a list whose signature matches.
 *
 *   java tools/SigningKey.java              makes the key, if this computer doesn't have it yet, and shows the public key
 *   java tools/SigningKey.java sign FILE... signs each file: FILE.sig goes next to it
 *
 * The private key is kept in .kelp-signing\signing-key.pk8 in your user folder, and nowhere else: never in a repo,
 * never shown. Back that folder up somewhere safe (a USB stick in a drawer). If it's lost, no update or Store list can
 * be signed again until everyone installs a new Kelp, with a new key, by hand.
 * Package.java (update.json) and Squid's Build.java (store.json) use the same file.
 */
public class SigningKey {
    static final Path FOLDER = Path.of(System.getProperty("user.home"), ".kelp-signing");
    static final Path PRIVATE = FOLDER.resolve("signing-key.pk8");
    /** The public half, saved when the key is made, so it can be shown again. It isn't a secret. */
    static final Path PUBLIC = FOLDER.resolve("signing-key.pub");

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("sign")) {
            if (args.length == 1) throw new IllegalArgumentException("Say which files to sign: java tools/SigningKey.java sign store.json");
            PrivateKey key = load();
            for (int i = 1; i < args.length; i++) {
                Path file = Path.of(args[i]);
                Path sig = file.resolveSibling(file.getFileName() + ".sig");
                byte[] bytes = Files.readAllBytes(file);
                String signature = sign(key, bytes);
                checkTrusted(bytes, signature);
                Files.writeString(sig, signature + "\n");
                System.out.println("Signed " + file + " -> " + sig.getFileName());
            }
            return;
        }
        if (!Files.exists(PRIVATE)) make();
        else System.out.println("This computer already has the signing key (" + PRIVATE + ").");
        if (Files.exists(PUBLIC)) System.out.println("Public key: " + Files.readString(PUBLIC).strip());
    }

    /** Makes a new key pair. The private key file is made private before the key is written into it. */
    static void make() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(FOLDER);
        try {
            // On Mac and Linux, only you can read it (Windows already keeps your user folder to you)
            Files.createFile(PRIVATE, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException windows) {
            Files.createFile(PRIVATE);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException(PRIVATE + " is already there; it was not replaced.");
        }
        Files.write(PRIVATE, pair.getPrivate().getEncoded());
        Files.writeString(PUBLIC, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n");
        System.out.println("Made the signing key in " + FOLDER + ". Back that folder up somewhere safe!");
    }

    static PrivateKey load() throws Exception {
        if (!Files.exists(PRIVATE)) {
            throw new IllegalStateException("There's no signing key on this computer (" + PRIVATE + "). Copy it back from your backup."
                    + " Only make a new one (java tools/SigningKey.java) if it's lost for good: then every player needs a new Kelp installed by hand.");
        }
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(PRIVATE)));
    }

    /**
     * Makes sure Kelp will accept the signature: it's checked with the public key in src/kelp/Signatures.java, the one
     * every Kelp has. A key that isn't that one would make every player turn the list down.
     */
    static void checkTrusted(byte[] bytes, String signature) throws Exception {
        Path source = Path.of("src", "kelp", "Signatures.java");
        if (!Files.exists(source)) return; // not run from the Kelp folder
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("PUBLIC_KEY = \"([A-Za-z0-9+/=]+)\"").matcher(Files.readString(source));
        if (!m.find()) throw new IllegalStateException("Couldn't find PUBLIC_KEY in " + source);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(m.group(1)))));
        verifier.update(bytes);
        if (!verifier.verify(Base64.getDecoder().decode(signature))) {
            throw new IllegalStateException("The signing key on this computer isn't the one Kelp trusts (PUBLIC_KEY in " + source + "), so nothing was signed.");
        }
    }

    /** The signature of exactly these bytes, as base64 text: what goes in a .sig file. */
    static String sign(PrivateKey key, byte[] bytes) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(bytes);
        return new String(Base64.getEncoder().encode(signer.sign()), StandardCharsets.US_ASCII);
    }
}
