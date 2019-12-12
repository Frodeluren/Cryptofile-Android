package net.cryptofile.app.ui.fileDownload;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import net.cryptofile.app.MainActivity;
import net.cryptofile.app.R;
import net.cryptofile.app.data.CryptoService;
import net.cryptofile.app.data.FileService;
import net.cryptofile.app.data.Result;

import org.apache.tika.io.IOUtils;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileDownloadActivity extends AppCompatActivity {

    ProgressBar progressBar;
    Button downloadButton;
    Result response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_download);

        TextInputEditText uuidInputField =  findViewById(R.id.textInputEditText);
        downloadButton = findViewById(R.id.downloadSubmitBtn);
        progressBar = findViewById(R.id.downloadProgressBar);
        TextView progressText = findViewById(R.id.downloadStatusText);
        downloadButton.setEnabled(false);
        progressBar.setVisibility(View.INVISIBLE);

        uuidInputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                boolean isOnlyUuid = uuidInputField.getText().toString().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                boolean isUuidWithKey = uuidInputField.getText().toString().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}:.+=$");

                if(isOnlyUuid || isUuidWithKey){
                    downloadButton.setEnabled(true);
                    if(isOnlyUuid){ progressText.setText("Detected UUID!");}
                    if(isUuidWithKey){ progressText.setText("Detected UUID and key!");}

                } else {
                    downloadButton.setEnabled(false);
                    progressText.setText("Not a valid uuid!");
                }

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        downloadButton.setOnClickListener(v -> {
            if (uuidInputField.getText().toString().equals("")){
                Toast.makeText(this, "Input is empty!", Toast.LENGTH_LONG).show();
            } else {
                progressBar.setVisibility(View.VISIBLE);
                downloadFile(uuidInputField.getText().toString());
                downloadButton.setEnabled(false);
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    private void downloadFile(String uuidInput){
        new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... voids) {
                boolean isOnlyUuid = uuidInput.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                boolean isUuidWithKey = uuidInput.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}:.+=$");

                try {
                    String urlPath = "http://cryptofile.net:8080/get/";
                    if (isOnlyUuid) {    // This runs if UUID is valid but have no key
                        URL url = new URL(urlPath + uuidInput);

                        // Grabbing file title from server
                        String title = getDetailFromServer("title", uuidInput);

                        // Grabbing file filetype from server
                        String filetype = getDetailFromServer("filetype", uuidInput);

                        FileService.addFile(uuidInput, title, filetype);
                    } else if (isUuidWithKey) { // This runs if there is a UUID and a key attached to it
                        CryptoService.saveKey(uuidInput);

                        String[] split = uuidInput.split(":", 2);
                        String uuid = split[0];


                        // Grabbing file title from server
                        String title = getDetailFromServer("title", uuid);

                        // Grabbing file filetype from server
                        String filetype = getDetailFromServer("filetype", uuid);

                        // Adds file to filelist
                        FileService.addFile(uuid, title, filetype);

                        // Getting the file itself
                        URL url = new URL(urlPath + uuid);
                        InputStream in = url.openStream();
                        Path filePath = Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(), uuid + "." + filetype);

                        byte[] decryptedBytes = CryptoService.decrypt(CryptoService.getKey(uuid), IOUtils.toByteArray(in));
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(decryptedBytes);
                        Files.copy(byteArrayInputStream, filePath);
                        in.close();

                        response = new Result.Success<>(true);
                    }


                } catch (Exception e) {
                    //Toast.makeText(, "Enter a valid UUID or UUID with key", Toast.LENGTH_LONG).show();
                    response = new Result.Error(e);
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid){
                super.onPostExecute(aVoid);
                redirect();
            }
        }.execute();
    }

    public String getDetailFromServer(String detail, String uuid) throws Exception {
        String urlPath = "http://cryptofile.net:8080/get/";
        String detailString = "";
        URL urlFiletype = new URL(urlPath + detail + "/" + uuid);
        System.out.println("URL: " + urlFiletype);
        HttpURLConnection c = (HttpURLConnection) urlFiletype.openConnection();
        c.setRequestMethod("GET");
        if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            detailString = content.toString();
            System.out.println("Recieved " + detail + ": " + detailString);
        } else {
            System.out.println("Could not get the " + detail + ": " + c.getErrorStream());
        }
        c.disconnect();
        return detailString;
    }

    private void redirect(){

        if (response instanceof Result.Success) {
            progressBar.setVisibility(View.INVISIBLE);
            Toast.makeText(this, "File successfully downloaded!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
        } else {
            Toast.makeText(this, "Something went wrong, file failed to be uploaded!", Toast.LENGTH_LONG).show();
            downloadButton.setEnabled(true);
        }
    }
}
