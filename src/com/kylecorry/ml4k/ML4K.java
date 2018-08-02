package com.kylecorry.ml4k;

import com.google.appinventor.components.runtime.*;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.YaVersion;
import com.google.appinventor.components.runtime.util.AsynchUtil;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.UsesLibraries;

import android.app.Activity;

import com.google.gson.*;

import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.Scanner;

@DesignerComponent(version = YaVersion.LABEL_COMPONENT_VERSION,
        description = "This provides an interface for the Machine Learning for Kids website.",
        category = ComponentCategory.EXTENSION,
        nonVisible = true,
        iconName = "aiwebres/ml4k.png")
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.INTERNET")
@UsesLibraries(libraries = "gson.jar")
public final class ML4K extends AndroidNonvisibleComponent {

    private static final String ENDPOINT_URL = "https://machinelearningforkids.co.uk/api/scratch/%s/classify";
    private static final String DATA_KEY = "data";

    private final Activity activity;

    private String key = "";

    public ML4K(ComponentContainer container) {
        super(container.$form());
        activity = container.$context();
    }

    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING)
    @SimpleProperty(description = "The API key for the ML4K app.")
    public void Key(String key) {
        this.key = key;
    }

    @SimpleProperty
    public String Key() {
        return key;
    }

    @SimpleFunction(description = "Get the classification for the image.")
    public void ClassifyImage(final String path) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    if (key == null || key.isEmpty()) {
                      GotError(path, "API key not set");
                      return;
                    }
                    // Get the data
                    final String imageData = getImageData(path);
                    String dataStr = "{\"data\": " + "\"" + URLEncoder.encode(imageData, "UTF-8") + "\"}";

                    // Setup the request
                    URL url = new URL(getURL());
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setFixedLengthStreamingMode(dataStr.length());
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Accept", "*/*");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:61.0) Gecko/20100101 Firefox/61.0");
                    conn.setRequestProperty("Connection", "keep-alive");
                    conn.setRequestProperty("Content-Type", "application/json");

                    // Send image data
                    conn.setDoOutput(true);
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(dataStr);
                    os.flush();
                    os.close();

                    // Parse
                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        final String json = read(conn.getInputStream());
                        conn.disconnect();

                        // Parse JSON
                        try {
                            Classification classification = Classification.fromJson(path, json);
                            GotClassification(classification.data, classification.classification, classification.confidence);
                        } catch (JsonParseException e) {
                            GotError(path, "Bad data from server: " + json);
                        }
                    } else {
                        GotError(path, "Bad response from server: " + conn.getResponseCode());
                        conn.disconnect();
                    }

                } catch (UnsupportedEncodingException e) {
                    GotError(path, "Could not encode image");
                } catch (MalformedURLException e) {
                    GotError(path, "Could not generate URL");
                } catch (IOException e) {
                    GotError(path, "No Internet connection.");
                }

            }
        });
    }

    @SimpleFunction(description = "Get the classification for the text.")
    public void ClassifyText(final String data) {
        runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    if (key == null || key.isEmpty()) {
                      GotError(data, "API key not set");
                      return;
                    }
                    // Get the data
                    String urlStr = getURL() + "?data=" + URLEncoder.encode(data, "UTF-8");

                    // Setup the request
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:61.0) Gecko/20100101 Firefox/61.0");

                    // Parse
                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        final String json = read(conn.getInputStream());
                        conn.disconnect();

                        // Parse JSON
                        try {
                            Classification classification = Classification.fromJson(data, json);
                            GotClassification(classification.data, classification.classification, classification.confidence);
                        } catch (JsonParseException e) {
                            GotError(data, "Bad data from server: " + json);
                        }
                    } else {
                        GotError(data, "Bad response from server: " + conn.getResponseCode());
                        conn.disconnect();
                    }

                } catch (UnsupportedEncodingException e) {
                    GotError(data, "Could not encode text");
                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    GotError(data, "Could not generate URL");
                } catch (IOException e) {
                    GotError(data, "No Internet connection.");
                }


            }
        });
    }

    /**
     * Event indicating that a classification got an error.
     *
     * @param data  The data
     * @param error The error
     */
    @SimpleEvent
    public void GotError(final String data, final String error) {
        final Component component = this;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                EventDispatcher.dispatchEvent(component, "GotError", data, error);
            }
        });
    }

    /**
     * Event indicating that a classification has finished.
     *
     * @param data           The data
     * @param classification The classification
     * @param confidence     The confidence of the classification
     */
    @SimpleEvent
    public void GotClassification(final String data, final String classification, final double confidence) {
        final Component component = this;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                EventDispatcher.dispatchEvent(component, "GotClassification", data, classification, confidence);
            }
        });
    }

    // Helpers

    /**
     * Read an input stream to a String.
     *
     * @param is The input stream.
     * @return The data from the input stream as a String.
     */
    private String read(InputStream is) {
        Scanner scanner = new Scanner(is);

        StringBuilder sb = new StringBuilder();

        while (scanner.hasNextLine()) {
            sb.append(scanner.nextLine());
        }

        return sb.toString();
    }

    /**
     * Get the ENDPOINT_URL for ML4K.
     *
     * @return The ENDPOINT_URL with the key for ML4K.
     */
    private String getURL() {
        return String.format(ENDPOINT_URL, key);
    }

    /**
     * Turn the data of an image into base 64.
     *
     * @param path The path to the image.
     * @return The data of the image as a base 64 string.
     */
    private String getImageData(final String path) {
        try {
            Scanner scanner = new Scanner(new FileReader(path));
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNext()) {
                sb.append(scanner.next());
            }
            scanner.close();
            byte[] encodedBytes = Base64.getEncoder().encode(sb.toString().getBytes());
            return new String(encodedBytes);
        } catch (FileNotFoundException e) {
            GotError(path, "File not found");
        }
        return "";
    }


    private void runInBackground(Runnable runnable) {
        AsynchUtil.runAsynchronously(runnable);
    }


    private static class Classification {
        private String data;
        private String classification;
        private double confidence;

        private Classification(String data, String classification, double confidence) {
            this.data = data;
            this.classification = classification;
            this.confidence = confidence;
        }

        private static Classification fromJson(String data, String json) throws JsonParseException {
            JsonElement jsonElement = new JsonParser().parse(json);
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            JsonObject value = jsonArray.get(0).getAsJsonObject();

            final String className = value.get("class_name").getAsString();
            final double confidence = value.get("confidence").getAsDouble();
            return new Classification(data, className, confidence);
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getClassification() {
            return classification;
        }

        public void setClassification(String classification) {
            this.classification = classification;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
    }


}