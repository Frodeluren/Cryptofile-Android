package net.cryptofile.app.data;

import android.security.keystore.KeyProperties;
import android.util.Log;

import org.apache.tika.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoService {
    private static final String TAG = "CryptoService";
    private static KeyStore keyStore;
    private static char[] password = "password".toCharArray();
    private static String keyLocation = "/data/data/net.cryptofile.app/files/cryptokeys.bks"; // getFilesdir don't work in static classes
    private static String cacheFileLocation = "/data/data/net.cryptofile.app/cache/uploadfile.tmp";
    private static String type = KeyStore.getDefaultType();
    public static int MAX_BUFFER_SIZE = 1024000 * 15;

    static {
        try {
            if (!(new File(keyLocation).exists())) {
                keyStore = KeyStore.getInstance(type);
                keyStore.load(null, null);
                keyStore.store(new FileOutputStream(keyLocation), password);
            }

            keyStore = KeyStore.getInstance(type);
            keyStore.load(new FileInputStream(keyLocation), password);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static SecretKey generateKey() throws Exception {
        final KeyGenerator keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
        keyGen.init(256);
        return keyGen.generateKey();
    }

    public static void storeKey(SecretKey key, String uuid) throws Exception {
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(key);
        KeyStore.ProtectionParameter kspp = new KeyStore.PasswordProtection(password);

        System.out.println("Default keystore: " + type);


        keyStore.setEntry(uuid, secretKeyEntry, kspp);
        keyStore.store(new FileOutputStream(keyLocation), password);


        System.out.println("Stored key: " + Base64.getEncoder().encodeToString(getKey(uuid).getEncoded()));
    }

    public static SecretKey getKey(String uuid) throws Exception {
        return (SecretKey) keyStore.getKey(uuid, password);
    }

    public static void saveKey(String uuidAndKey) throws Exception {
        String[] splittedString = uuidAndKey.split(":", 2);
        byte[] keyBytes = Base64.getDecoder().decode(splittedString[1].getBytes(StandardCharsets.UTF_8));
        SecretKey secretKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
        storeKey(secretKey, splittedString[0]);
    }

    public static ArrayList<String> getAllAliases() throws KeyStoreException {
        return Collections.list(keyStore.aliases());
    }

    public static byte[] encrypt(SecretKey key, byte[] fileBytes) throws Exception {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        byte[] encryptedFileBytes = cipher.doFinal(fileBytes);

        System.out.println("IV encrypt: " + new String(iv, StandardCharsets.UTF_8));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(iv);
        outputStream.write(encryptedFileBytes);

        return outputStream.toByteArray();
    }

    public static File encrypt(SecretKey key, InputStream inputStream) throws Exception {
        deleteCache();
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        //byte[] encryptedFileBytes = cipher.doFinal(fileBytes);

        System.out.println("IV encrypt: " + new String(iv, StandardCharsets.UTF_8));

        //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        FileOutputStream outputStream = new FileOutputStream(cacheFileLocation, true);
        //CipherInputStream cis = new CipherInputStream(inputStream, cipher);
        outputStream.write(iv);


        int fileSize = inputStream.available(); // 52428800
        Log.println(Log.INFO, TAG, "Filesize is detected to be" + fileSize);

        // Checks if the selected file is bigger than max buffersize.
        // If the filesize is lower than max buffersize, then the encrypted file will be written without a CipherInputStream which makes the encryption much faster.
        // If the file is too big, then it is required with a CipherInputStream. The whole file wont be a huge byte[] variable when this is done.
        if (fileSize > MAX_BUFFER_SIZE) {
            CipherInputStream cis = new CipherInputStream(inputStream, cipher);
            int read;
            byte[] buffer = new byte[MAX_BUFFER_SIZE];
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(cis.read(buffer, 0, read));
            }
            cis.close();
        } else if(fileSize < MAX_BUFFER_SIZE && fileSize > 1) {
            outputStream.write(cipher.doFinal(IOUtils.toByteArray(inputStream)));
        } else {
            Log.println(Log.WARN, TAG, "Filesize is detected to be zero or null.");
        }
        outputStream.close();


        return new File(cacheFileLocation);
    }

    public static byte[] decrypt(SecretKey key, byte[] encryptedBytes) throws Exception {
        byte[] iv = Arrays.copyOf(encryptedBytes, 12);
        byte[] encryptedFileBytes = Arrays.copyOfRange(encryptedBytes, 12, encryptedBytes.length);

        System.out.println("IV decrypt: " + new String(iv, StandardCharsets.UTF_8));

        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        final GCMParameterSpec spec = new GCMParameterSpec(128, iv);

        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(encryptedFileBytes);
    }

    public static void delete(String uuid) throws Exception {
        keyStore.deleteEntry(uuid);
    }

    private static void deleteCache() throws Exception {
        new FileOutputStream(cacheFileLocation).write("".getBytes());
    }
}