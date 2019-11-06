package net.cryptofile.app.ui.slideshow;

import android.app.Application;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import net.cryptofile.app.data.CryptoService;
import net.cryptofile.app.data.model.Privatekey;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class PrivatekeyViewModel extends AndroidViewModel {
    MutableLiveData<List<Privatekey>> privkeys;
    MutableLiveData<Privatekey> selected = new MutableLiveData<>();

    RequestQueue requestQueue;

    public PrivatekeyViewModel(Application context) {
        super(context);
        requestQueue = Volley.newRequestQueue(context);
    }


    public LiveData<List<Privatekey>> getPrivateKeys() throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidKeySpecException {
        if (privkeys == null){
            privkeys = new MutableLiveData<>();
            loadPrivkeys();
        }
        return privkeys;
    }


    protected void loadPrivkeys() throws NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException, InvalidKeySpecException {
        // TODO: 05.11.2019 Load privatekey list from local database
        List<Privatekey> privatekeyList = new ArrayList<>();
        CryptoService keys = null;
        String pubkey = null;
        String privkey = null;

        for (int i = 0; i < 3; i++) {
            try {
                keys = new CryptoService(1024);
                keys.createKeys();
                pubkey = Base64.getEncoder().encodeToString(keys.getPublicKey().getEncoded());
                privkey = Base64.getEncoder().encodeToString(keys.getPrivateKey().getEncoded());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchProviderException e) {
                e.printStackTrace();
            }

            System.out.println("Public key: " + pubkey);
            System.out.println("Private key: " + privkey);
            if (CryptoService.isPair(privkey, pubkey)) {
                System.out.println("The keys is working!!!");
            } else {
                System.out.println("The keys does not work :(");
            }

            privatekeyList.add(new Privatekey(UUID.randomUUID().toString(), privkey, pubkey));
        }
        this.privkeys.setValue(privatekeyList);
    }

    public LiveData<Privatekey> getSelected() {
        return selected;
    }

    void setSelected(Privatekey selected){
        this.selected.setValue(selected);
    }
}