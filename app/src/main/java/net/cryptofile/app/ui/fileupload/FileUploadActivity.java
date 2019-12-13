package net.cryptofile.app.ui.fileupload;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import net.cryptofile.app.MainActivity;
import net.cryptofile.app.R;
import net.cryptofile.app.data.Result;
import net.cryptofile.app.tasks.TaskDelegate;
import net.cryptofile.app.tasks.UploadTask;

import org.apache.tika.Tika;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.DecimalFormat;

public class FileUploadActivity extends AppCompatActivity {

    private static final int REQUEST_GET_SINGLE_FILE = 1;

    TextInputEditText detectedFiletypeText;
    TextView fileLocationText;
    Button submitBtn;
    TextInputEditText titleInput;

    String stageString;

    Result response;
    String filePath;
    Uri selectedFile;
    String CACHE_PATH;

    TextView statusText;
    ProgressBar progressBar;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_upload_activity);

        // Stuff that could be picked up from settings
        CACHE_PATH = getCacheDir().getPath();
        String keyStorePath = getFilesDir() + "/cryptokeys.bks";
        String password = "password";


        // Initialize page components
        final Button selectFilebutton = findViewById(R.id.selectUploadFilebutton);
        titleInput = findViewById(R.id.textInputEditText);
        fileLocationText = findViewById(R.id.textViewFilelocation);
        detectedFiletypeText = findViewById(R.id.textViewDetectedFileType);
        submitBtn = findViewById(R.id.uploadSubmitBtn);
        statusText = findViewById(R.id.uploadStatusText);
        progressBar = findViewById(R.id.uploadProgressBar);

        progressBar.setVisibility(View.GONE);


        selectFilebutton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "Select File"), REQUEST_GET_SINGLE_FILE);
        });

        submitBtn.setOnClickListener(v -> {
            submitBtn.setEnabled(false);
            statusText.setText("Uploading...");
            progressBar.setVisibility(View.VISIBLE);



            try {
                TaskDelegate taskDelegate = new TaskDelegate() {
                    @Override
                    public void taskCompletionResult(Result result) {
                        try {
                            response = result;
                            redirect();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void taskProgress(float progress) {

                        DecimalFormat df = new DecimalFormat();
                        df.setMaximumFractionDigits(1);
                        statusText.setText(stageString + df.format(progress) + "%");
                    }

                    @Override
                    public void taskStage(String stage) {
                        stageString = stage;
                    }
                };

                // Generate task AsyncTask
                UploadTask uploadTask = new UploadTask(CACHE_PATH, keyStorePath, password, taskDelegate);

                InputStream inputStream = getContentResolver().openInputStream(selectedFile);
                uploadTask.execute(inputStream, titleInput.getText().toString(), detectedFiletypeText.getText().toString());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            //uploadTask.getStatus().equals(AsyncTask.Status.FINISHED);
            //submitFile(fileAsBytes, titleInput.getText().toString(), detectedFiletypeText.getText().toString());
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            if (resultCode == RESULT_OK) {
                if (requestCode == REQUEST_GET_SINGLE_FILE) {
                    selectedFile = data.getData();

                    filePath = selectedFile.getPath();

                    if (filePath != null) {
                        String ft = new Tika().detect(filePath); // Detects filetype
                        if (!(ft.isEmpty() || ft.matches("application/octet-stream"))) {
                            detectedFiletypeText.setText(ft.split("/")[1]);
                        }
                        fileLocationText.setText(filePath.substring(filePath.lastIndexOf("/") + 1));

                        //statusText.setText("Encrypting...");
                        //progressBar.setVisibility(View.VISIBLE);
                    } else {
                        System.out.println("Path is null!");
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void redirect() throws Exception {
        if (response instanceof Result.Success) {
            //CryptoService.storeKey(key, returnedUuid);
            //FileService.addFile(returnedUuid, titleInput.getText().toString(), detectedFiletypeText.getText().toString());
            Toast.makeText(this, "File successfully uploaded", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
        } else {
            Toast.makeText(this, "Something went wrong, file failed to be uploaded!", Toast.LENGTH_LONG).show();
            submitBtn.setEnabled(true);
        }
    }

}
