package com.optum.speech.med;

// [START speech_quickstart]
// Imports the Google Cloud client library
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.text.NumberFormat;
import java.text.DecimalFormat;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.ctakes.core.cc.pretty.plaintext.PrettyTextWriter;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.util.JCasUtil;


public class MedSmart {

    // Reuse the pipeline for demo purposes
    static AnalysisEngine pipeline;
    private static final NumberFormat formatter = new DecimalFormat("#0.00000");

    /** Demonstrates using the Speech API to transcribe an audio file. */
    public static void main(String... args) throws Exception {
        // Instantiates a client
        try (SpeechClient speechClient = SpeechClient.create()) {

            // The path to the audio file to transcribe
            String fileName = "/Users/raga/Gayathiri/hackathon/audio_med.raw";//"/Users/raga/Downloads/audio.raw";  //"/Users/raga/Gayathiri/hackathon/medical_audio.raw"; //"./resources/audio.raw";

            // Reads the audio file into memory
            Path path = Paths.get(fileName);
            byte[] data = Files.readAllBytes(path);
            ByteString audioBytes = ByteString.copyFrom(data);

            // Builds the sync recognize request
            RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                            .setEncoding(AudioEncoding.LINEAR16)
                            .setSampleRateHertz(16000)
                            .setLanguageCode("en-US")
                            .build();
            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

            // Performs speech recognition on the audio file
            RecognizeResponse response = speechClient.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();

            System.out.println("Before Results" + results);
            String transcript = null ;
            for (SpeechRecognitionResult result : results) {
                // There can be several alternative transcripts for a given chunk of speech. Just use the
                // first (most likely) one here.
                System.out.println("Inside for");

                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                System.out.printf("Transcription: %s%n", alternative.getTranscript());

                transcript =  alternative.getTranscript();
            }


            //new

            long start = System.currentTimeMillis();

            AggregateBuilder aggregateBuilder;
           // try {
                aggregateBuilder = Pipeline.getAggregateBuilder();
                pipeline = aggregateBuilder.createAggregate();
            //} catch (Exception e) {
               // throw new ServletException(e);
           // }


            if (transcript != null && transcript.trim().length() > 0) {
                try {
                    /*
                     * Set the document text to process And run the cTAKES pipeline
                     */
                    JCas jcas = pipeline.newJCas();
                    jcas.setDocumentText(transcript);
                    pipeline.process(jcas);
                    String format = "xml";
                    String result = formatResults(jcas, format);
                    jcas.reset();
                    String elapsed = formatter
                            .format((System.currentTimeMillis() - start) / 1000d);
                    if ("html".equalsIgnoreCase(format)
                            || "pretty".equalsIgnoreCase(format)) {
                        result += "<p/><i> Processed in " + elapsed + " secs</i>";
                    }

                    System.out.println("result: " + result);
                   // out.println(result);
                } catch (Exception e) {
                    throw new Exception(e);
                }
            }

        }


    }

    public static String formatResults(JCas jcas, String format
                               ) throws Exception {
        StringBuffer sb = new StringBuffer();
        /**
         * Select the types/classes that you are interested We are selecting
         * everything including TOP for demo purposes
         */
        Collection<TOP> annotations = JCasUtil.selectAll(jcas);
        if ("html".equalsIgnoreCase(format)) {
           // response.setContentType("text/html");

            sb.append("<html><head><title></title></head><body><table>");
            for (TOP a : annotations) {

                sb.append("<tr>");
                sb.append("<td>" + a.getType().getShortName() + "</td>");
                extractFeatures(sb, (FeatureStructure) a);
                sb.append("</tr>");
            }
            sb.append("</table></body></html>");
        } else if ("pretty".equalsIgnoreCase(format)) {
            StringWriter sw = new StringWriter();
            BufferedWriter writer = new BufferedWriter(sw);
            Collection<Sentence> sentences = JCasUtil.select(jcas,
                    Sentence.class);
            for (Sentence sentence : sentences) {
                PrettyTextWriter.writeSentence(jcas, sentence, writer);
            }
            writer.close();
            sb.append("<html><head><title></title></head><body><table><pre>");
            sb.append(sw.toString());
            sb.append("</pre></table></body></html>");

        } else {
          //  response.setContentType("application/xml");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            XmiCasSerializer.serialize(jcas.getCas(), output);
            sb.append(output.toString());
            output.close();
        }
        return sb.toString();
    }

    public static void extractFeatures(StringBuffer sb, FeatureStructure fs) {

        List<?> plist = fs.getType().getFeatures();
        for (Object obj : plist) {
            if (obj instanceof Feature) {
                Feature feature = (Feature) obj;
                String val = "";
                if (feature.getRange().isPrimitive()) {
                    val = fs.getFeatureValueAsString(feature);
                } else if (feature.getRange().isArray()) {
                    // Flatten the Arrays
                    FeatureStructure featval = fs.getFeatureValue(feature);
                    if (featval instanceof FSArray) {
                        FSArray valarray = (FSArray) featval;
                        for (int i = 0; i < valarray.size(); ++i) {
                            FeatureStructure temp = valarray.get(i);
                            extractFeatures(sb, temp);
                        }
                    }
                }
                if (feature.getName() != null
                        && val != null
                        && val.trim().length() > 0
                        && !"confidence".equalsIgnoreCase(feature
                        .getShortName())) {
                    sb.append("<td>" + feature.getShortName() + "</td>");
                    sb.append("<td>" + val + "</td>");
                }
            }
        }

    }

}
// [END speech_quickstart]