package com.cavalcante.giovanni;

import com.thingmagic.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    private static final String RASP_PI_PORT = "tmr:///dev/ttyACM0";
    private static final String WINDOWS_PORT = "tmr:///COM4";

    public static void setTrace(Reader r, String args[]) {
        if (args[0].toLowerCase().equals("on")) {
            r.addTransportListener(r.simpleTransportListener);
        }
    }

    private static Reader setup() {
        Reader r = null;

        // Create reader object
        String readerURI = RASP_PI_PORT;

        try {
            r = Reader.create(readerURI);
        } catch (ReaderException e) {
            String formattedMessage = "Não foi possível criar o Reader com a URI " + readerURI;
            System.out.println(formattedMessage);
            e.printStackTrace();
        }

        try {
            r.connect();
        } catch (ReaderException e) {
            String formattedMessage = "Não foi possível conectar ao Reader com a URI " + readerURI;
            System.out.println(formattedMessage);
            e.printStackTrace();
        }

        // Assign supported region
        try {
            if (Reader.Region.UNSPEC == r.paramGet("/reader/region/id")) {
                Reader.Region[] supportedRegions = (Reader.Region[]) r
                        .paramGet(TMConstants.TMR_PARAM_REGION_SUPPORTEDREGIONS);
                if (supportedRegions.length < 1) {
                    throw new Exception("Reader doesn't support any regions");
                } else {
                    r.paramSet("/reader/region/id", supportedRegions[0]);
                }
            }
        } catch (Exception e) {
            String formattedMessage = "Erro ao settar uma região ao Reader";
            System.out.println(formattedMessage);
            e.printStackTrace();
        }

        return r;
    }

    private static void read(Reader r) {
        TagReadData[] tagReads = null;
        int[] antennaList = {1, 2, 3, 4};

        try {
            SimpleReadPlan plan = new SimpleReadPlan(antennaList, TagProtocol.GEN2, null, null, 1000);
            r.paramSet(TMConstants.TMR_PARAM_READ_PLAN, plan);
        } catch (ReaderException e) {
            String formattedMessage = "Não foi possível criar o plano de leitura para o Reader";
            System.out.println(formattedMessage);
            e.printStackTrace();
        }

        // Read tags
        try {
            tagReads = r.read(500);
        } catch (ReaderException e) {
            String formattedMessage = "Erro durante a leitura das tags";
            System.out.println(formattedMessage);
            e.printStackTrace();
        }
        // Print tag reads
        for (TagReadData tr : tagReads) {
            System.out.println("EPC: " + tr.epcString());
        }
    }

    public static void main(String argv[]) {
        Reader reader = setup();
        read(reader);
        reader.destroy();
    }

    static void sendHTTPRequest(List<String> tagIDs) {
        HttpURLConnection con = null;
        try {
            URL url = new URL("http://52.52.244.199:80/");
            con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");

            Map<String, String> parameters = new HashMap<>();
            parameters.put("tags", tagIDs.toString());

            con.setDoOutput(true);
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            out.writeBytes(getParamsString(parameters));
            out.flush();
            out.close();

            con.connect();

        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    static String getParamsString(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            result.append("&");
        }

        String resultString = result.toString();
        return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
    }

}
