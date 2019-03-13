/*
 *  * Copyright © Wynntils - 2019.
 */

package com.wynntils.webapi.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wynntils.ModCore;
import com.wynntils.Reference;
import com.wynntils.webapi.WebManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.CryptManager;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import javax.crypto.SecretKey;
import javax.xml.bind.DatatypeConverter;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class WynntilsAccount {

    private static ExecutorService service = Executors.newSingleThreadScheduledExecutor();

    String token;
    boolean ready = false;

    HashMap<String, String> encondedConfigs = new HashMap<>();

    public WynntilsAccount() { }

    public String getToken() {
        return token;
    }

    public HashMap<String, String> getEncondedConfigs() {
        return encondedConfigs;
    }

    public void updateDiscord(String id, String username) {
        if(!ready) return;

        service.submit(() -> {
            try {
                URLConnection st = new URL(WebManager.apiUrls.get("UserAccount") + "updateDiscord/" + token + "/" + id + "/" + username).openConnection();
                st.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");

                Reference.LOGGER.info("Updating user Discord ID");
            }catch (Exception ex) { ex.printStackTrace(); }
        });
    }

    public void uploadConfig(String fileName, String base64, Consumer<Boolean> callback) {
        if(!ready) callback.accept(false);

        service.submit(() -> {
            try {
                URLConnection st = new URL(WebManager.apiUrls.get("UserAccount") + "uploadConfig/" + token).openConnection();

                //HeyZeer0: Request below
                JsonObject body = new JsonObject();
                body.addProperty("fileName", fileName);
                body.addProperty("base64", base64);
                // {"fileName":"<file-name>", "base64":"<base-64>"}

                byte[] bodyBytes = body.toString().getBytes(Charsets.UTF_8);
                st.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
                st.setRequestProperty("Content-Length", "" + bodyBytes.length);
                st.setRequestProperty("Content-Type", "application/json");
                st.setDoOutput(true);

                OutputStream outputStream = null;
                try {
                    outputStream = st.getOutputStream();
                    IOUtils.write(bodyBytes, outputStream);
                }catch (Exception ex) {
                    ex.printStackTrace();
                    return;
                }
                finally {
                    IOUtils.closeQuietly(outputStream);
                }

                //HeyZeer0: Response below
                JsonObject response = new JsonParser().parse(IOUtils.toString(st.getInputStream())).getAsJsonObject();
                if (!response.has("result")) {
                    callback.accept(false);
                    return;
                }

                callback.accept(true);
            }catch (Exception ex) { ex.printStackTrace(); }
        });
    }

    public void login() {
        try {
            URLConnection st = new URL(WebManager.apiUrls.get("UserAccount") + "/requestEncryption").openConnection();
            st.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");

            JsonObject result = new JsonParser().parse(IOUtils.toString(st.getInputStream())).getAsJsonObject();

            byte[] publicKeyBy = DatatypeConverter.parseHexBinary(result.get("publicKeyIn").getAsString());

            SecretKey secretkey = CryptManager.createNewSharedKey();
            PublicKey publicKey = CryptManager.decodePublicKey(publicKeyBy);

            String s1 = (new BigInteger(1, CryptManager.getServerIdHash("", publicKey, secretkey))).toString(16);

            Minecraft mc = ModCore.mc();
            mc.getSessionService().joinServer(mc.getSession().getProfile(), mc.getSession().getToken(), s1);

            byte[] secretKeyEncrypted = CryptManager.encryptData(publicKey, secretkey.getEncoded());
            String lastKey = DatatypeConverter.printHexBinary(secretKeyEncrypted);

            URLConnection st2 = new URL(WebManager.apiUrls.get("UserAccount") + "/responseEncryption/").openConnection();

            JsonObject object = new JsonObject();
            object.addProperty("username", mc.getSession().getUsername());
            object.addProperty("key", lastKey);
            object.addProperty("version", Reference.VERSION);

            byte[] postAsBytes = object.toString().getBytes(Charsets.UTF_8);

            st2.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
            st2.setRequestProperty("Content-Length", "" + postAsBytes.length);
            st2.setRequestProperty("Content-Type", "application/json");
            st2.setDoOutput(true);

            OutputStream outputStream = null;
            try {
                outputStream = st2.getOutputStream();
                IOUtils.write(postAsBytes, outputStream);
            } finally {
                IOUtils.closeQuietly(outputStream);
            }

            JsonObject finalResult = new JsonParser().parse(IOUtils.toString(st2.getInputStream())).getAsJsonObject();
            if (finalResult.has("error")) {
                return;
            }

            if (finalResult.has("result")) {
                token = finalResult.get("authtoken").getAsString();
                ready = true;

                Reference.LOGGER.info("Succesfully connected to accounts!");

                JsonObject obj = finalResult.get("configFiles").getAsJsonObject();
                if (obj.entrySet().size() <= 0) return;

                for (Map.Entry<String, JsonElement> objs : obj.entrySet()) {
                    encondedConfigs.put(objs.getKey(), objs.getValue().getAsString());
                }
                return;
            }

        }catch (Exception ex) {
            ex.printStackTrace();
        }

        Reference.LOGGER.error("Failed to connect to Wynntils Accounts!");
    }

}
