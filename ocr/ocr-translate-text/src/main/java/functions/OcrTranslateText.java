package functions;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.translate.v3.LocationName;
import com.google.cloud.translate.v3.TranslateTextRequest;
import com.google.cloud.translate.v3.TranslateTextResponse;
import com.google.cloud.translate.v3.TranslationServiceClient;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.events.cloud.pubsub.v1.Message;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OcrTranslateText implements BackgroundFunction<Message> {
  private static final Logger logger = Logger.getLogger(OcrTranslateText.class.getName());

  private static final String PROJECT_ID = getenv("GCP_PROJECT");
  private static final String RESULT_BUCKET = System.getenv("RESULT_BUCKET");
  private static final String LOCATION_NAME = LocationName.of(PROJECT_ID, "global").toString();
  private static final Storage STORAGE = StorageOptions.getDefaultInstance().getService();

  @Override
  public void accept(Message message, Context context) {
    OcrTranslateApiMessage ocrMessage = OcrTranslateApiMessage.fromPubsubData(
        message.getData().getBytes(StandardCharsets.UTF_8));

    String targetLang = "hu";
    logger.info("Translating text into " + targetLang);

    // Translate text to target language
    String text = ocrMessage.getText();
    TranslateTextRequest request =
        TranslateTextRequest.newBuilder()
            .setParent(LOCATION_NAME)
            .setMimeType("text/plain")
            .setTargetLanguageCode(targetLang)
            .addContents(text)
            .build();

    TranslateTextResponse response;
    try (TranslationServiceClient client = TranslationServiceClient.create()) {
      response = client.translateText(request);
    } catch (IOException e) {
      // Log error (since IOException cannot be thrown by a function)
      logger.log(Level.SEVERE, "Error translating text: " + e.getMessage(), e);
      return;
    }
    if (response.getTranslationsCount() == 0) {
      return;
    }

    String translatedText = response.getTranslations(0).getTranslatedText();
    logger.info("Translated text: " + translatedText);


    // Instantiates a client
    try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
      // Set the text input to be synthesized
      SynthesisInput input = SynthesisInput.newBuilder().setText(translatedText).build();

      // Build the voice request
      VoiceSelectionParams voice =
              VoiceSelectionParams.newBuilder()
                      .setLanguageCode("hu-HU") // languageCode = "en_us"
                      .setSsmlGender(SsmlVoiceGender.FEMALE) // ssmlVoiceGender = SsmlVoiceGender.FEMALE
                      .build();

      // Select the type of audio file you want returned
      AudioConfig audioConfig =
              AudioConfig.newBuilder()
                      .setAudioEncoding(AudioEncoding.MP3) // MP3 audio.
                      .build();

      // Perform the text-to-speech request
      SynthesizeSpeechResponse responseAudio =
              textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

      // Get the audio contents from the response
      ByteString audioContents = responseAudio.getAudioContent();

      String newFileName = String.format(
              "%s_to_%s.mp3", ocrMessage.getFilename(), targetLang);

      logger.info(String.format("Saving result to %s in bucket %s", newFileName, RESULT_BUCKET));
      BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(RESULT_BUCKET, newFileName)).build();
      STORAGE.create(blobInfo, audioContents.toByteArray());
      logger.info("File saved");

    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error publishing translation save request: " + e.getMessage(), e);
    }

  }

  private static String getenv(String name) {
    String value = System.getenv(name);
    if (value == null) {
      logger.warning("Environment variable " + name + " was not set");
      value = "MISSING";
    }
    return value;
  }
}

