package net.cryptofile.app.tasks;

import android.os.AsyncTask;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.view.Surface;

import net.cryptofile.app.data.FileService;
import net.cryptofile.app.data.Result;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class UploadTask extends AsyncTask {

    private final String TAG = "UploadTask";
    private String keyLocation;
    private char[] password;
    private byte[] iv;
    private KeyStore keyStore;
    private String type = KeyStore.getDefaultType();

    private String title;
    private String filetype;

    private SecretKey secretKey;
    private CipherInputStream cipherInputStream;
    private DataInputStream dataInputStream;
    private TaskDelegate delegate;



    public UploadTask(String keyLocation, String password, TaskDelegate delegate) {
        this.keyLocation = keyLocation;
        this.password = password.toCharArray();
        this.delegate = delegate;

        try {
            if (!(new File(keyLocation).exists())) {
                keyStore = KeyStore.getInstance(type);
                keyStore.load(null, null);
                keyStore.store(new FileOutputStream(keyLocation), this.password);
            }

            keyStore = KeyStore.getInstance(type);
            keyStore.load(new FileInputStream(keyLocation), this.password);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();


    }

    @Override
    protected Object doInBackground(Object[] objects) { // File file, String title, String filetype
        HttpURLConnection c = null;
        InputStream inputStream = (InputStream) objects[0];
        title = (String) objects[1];
        filetype = (String) objects[2];

        try {


            secretKey = generateKey();
            cipherInputStream = getCipherStream(secretKey, inputStream);
            dataInputStream = new DataInputStream(cipherInputStream);
        } catch (Exception e){
            Log.println(Log.ERROR, TAG, "Failed to generate key or cipherstream: " + e.toString());
        }


        try {
            URL url = new URL("http://www.cryptofile.net:8080/add");
            String boundary = UUID.randomUUID().toString();
            c = (HttpURLConnection) url.openConnection();

            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "multipart/form-data;charset=UTF-8;boundary=----WebKitFormBoundary" + boundary);

            DataOutputStream request = new DataOutputStream(c.getOutputStream());

            // Title
            request.writeBytes("------WebKitFormBoundary" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"title\"\r\n");
            request.writeBytes("Content-Type: text/plain\r\n\r\n");
            request.writeBytes(title + "\r\n");

            // File
            request.writeBytes("------WebKitFormBoundary" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"binary\"\r\n");
            request.writeBytes("Content-Type: application/octet-stream\r\n\r\n");


            // Sending file as buffered bytes
            int availableBytes =  inputStream.available();

            System.out.println("Size: " + availableBytes);

            int fileSize = availableBytes;
            int progress = 0;
            int maxBufferSize = 4 * 1024;
            int bufferSize = Math.min(availableBytes, maxBufferSize);
            byte[] buffer = new byte[bufferSize];
            dataInputStream.readFully(buffer, 0, bufferSize);
            request.write(iv);
            while (bufferSize > 0) {
                try {
                    request.write(buffer, 0, bufferSize);
                    availableBytes = dataInputStream.available();
                    bufferSize = Math.min(availableBytes, maxBufferSize);
                    dataInputStream.readFully(buffer, 0, bufferSize);
                } catch (OutOfMemoryError e){
                    e.printStackTrace();
                    return new Result.MemoryError(e);
                }

                progress = progress + maxBufferSize;
                double status = 100 * progress/fileSize;
                publishProgress(status);
            }

            request.writeBytes("\r\n");

            // Filetype
            request.writeBytes("------WebKitFormBoundary" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"filetype\"\r\n");
            request.writeBytes("Content-Type: text/plain\r\n\r\n");
            request.writeBytes(filetype + "\r\n");

            request.writeBytes("------WebKitFormBoundary" + boundary + "--\r\n");
            request.flush();

            if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                String response = bufferedReader.readLine();
                System.out.println(response);
                c.getInputStream().close();
                return new Result.Success<>(response);
            }
            return new Result.Error(new IOException("Error uploading file: " + c.getResponseMessage()));
        } catch (Exception e) {
            System.err.println("Failed to call " + e);
            return new Result.Error(new IOException("Error creating item", e));

        } finally {
            if (c != null) c.disconnect();
        }
    }

    @Override
    protected void onProgressUpdate(Object[] object) {
        super.onProgressUpdate(object);
        //System.out.println("Progress: " + object[0]);
        Log.println(Log.INFO, TAG, "Uploading.. " + object[0] + "%");
        delegate.taskProgress((double) object[0]);
    }

    @Override
    protected void onPostExecute(Object object) {
        super.onPostExecute(object);
        try {
            if (object instanceof Result.Success) {
                String uuid = ((Result.Success) object).getData().toString();
                storeKey(secretKey, uuid);
                FileService.addFile(uuid, title, filetype);
                // Make notification say that the upload successed
                //Toast.makeText(, "File successfully uploaded", Toast.LENGTH_LONG);
                delegate.taskCompletionResult((Result.Success) object);

            }else {
                // Make notification say that the upload failed
                // Toast.makeText(, "File failed upload", Toast.LENGTH_LONG);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SecretKey generateKey() throws Exception {
        final KeyGenerator keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
        keyGen.init(256);
        return keyGen.generateKey();
    }

    private CipherInputStream getCipherStream(SecretKey key, InputStream inputStream) throws Exception {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        iv = cipher.getIV();

        return new CipherInputStream(inputStream, cipher);
    }

    private void storeKey(SecretKey key, String uuid) throws Exception {
        KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(key);
        KeyStore.ProtectionParameter kspp = new KeyStore.PasswordProtection(password);

        keyStore.setEntry(uuid, secretKeyEntry, kspp);
        keyStore.store(new FileOutputStream(keyLocation), password);
    }
}
