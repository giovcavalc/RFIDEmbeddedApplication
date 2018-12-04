package com.cavalcante.giovanni;

import com.thingmagic.*;
import gnu.io.CommPortIdentifier;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final boolean DEBUG = true;

    private static final String URI_SCHEME = "tmr://";

    private static CommPortIdentifier readerPortId;
    private static CommPortIdentifier arduinoPortId;
//    private static CommPortIdentifier arduinoShieldPortId;

//    private static final String LINUX = "Linux";
//    private static final String WINDOWS = "Windows";
//
//    private static final String RASP_PI_PORT = "tmr:///dev/ttyACM1";
//    private static final String WINDOWS_PORT = "eapi:///COM4";

    private static Reader reader;
    private static ControlePorta arduinoPort;

    private static void rfidSetup() {
        rfidConnectReader();

        if (reader == null) {
            return;
        }

        // Assign supported region
        try {
            if (Reader.Region.UNSPEC == reader.paramGet("/reader/region/id")) {
                Reader.Region[] supportedRegions = (Reader.Region[]) reader
                        .paramGet(TMConstants.TMR_PARAM_REGION_SUPPORTEDREGIONS);
                if (supportedRegions.length < 1) {
                    throw new Exception("Reader doesn't support any regions");
                } else {
                    reader.paramSet("/reader/region/id", supportedRegions[0]);
                }
            }
        } catch (Exception e) {
            String formattedMessage = "Erro ao settar uma região ao Reader";
            System.out.println(formattedMessage);
            if (DEBUG) e.printStackTrace();
        }
    }

    private static void rfidConnectReader() {
        System.out.println("Conectando RFIDReader...");
        CommPortIdentifier portId = null;
        Enumeration portsEnum = CommPortIdentifier.getPortIdentifiers();
        while (portsEnum.hasMoreElements()) {
            portId = (CommPortIdentifier) portsEnum.nextElement();
            String portName = portId.getName();
            System.out.println("Tentando conectar RFIDReader na arduinoPort: " + portName);

            try {
                String readerURI = URI_SCHEME + portName;
                reader = Reader.create(readerURI);
                // A Mercury API valida se a arduinoPort conectada de fato é a arduinoPort da leitora RFID.
                reader.connect();
                readerPortId = portId;
                System.out.println("RFIDReader conectado na porta: " + portName);
                return;
            } catch (ReaderException e) {
                String formattedMessage = "Não foi possível conectar ao Reader com a URI " + portName;
                System.out.println(formattedMessage);
                if (DEBUG) e.printStackTrace();
            }
        }
        // Nenhuma das portas conectou
        System.out.println("RFIDReader não conseguiu se conectar");
        reader = null;
        return;

    }

    private static void rfidReadPlanSetup() {
        int[] antennaList = {1, 2, 3, 4};

        if (reader == null) {
            return;
        }

        try {
            SimpleReadPlan plan = new SimpleReadPlan(antennaList, TagProtocol.GEN2, null, null, 1000);
            reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, plan);
        } catch (ReaderException e) {
            String formattedMessage = "Não foi possível criar o plano de leitura para o Reader";
            System.out.println(formattedMessage);
            if (DEBUG) e.printStackTrace();
        }
    }

    private static void rfidRead() throws InterruptedException{
        TagReadData[] tagReads = null;
        try {
            tagReads = reader.read(500);
        } catch (ReaderException e) {
            String formattedMessage = "Erro durante a leitura das tags";
            System.out.println(formattedMessage);
            if (DEBUG) e.printStackTrace();
        }
        // Print tag reads
        for (TagReadData tr : tagReads) {
            System.out.println("EPC: " + tr.epcString());
            arduinoPort.enviaDados(tr.epcString());
            Thread.sleep(1000);
        }
    }

    private static void arduinoSerialConnect() {
        CommPortIdentifier portId = null;
        Enumeration portsEnum = CommPortIdentifier.getPortIdentifiers();
        String portName = null;
        while (portsEnum.hasMoreElements()) {
            portId = (CommPortIdentifier) portsEnum.nextElement();
            if (portId.equals(readerPortId)) {
                continue;
            }

            portName = portId.getName();
            System.out.println("Tentando conectar Porta Serial na porta: " + portName);
        }
        arduinoPort = new ControlePorta(portName, 115200);
        arduinoPortId = portId;
        System.out.println("Arduino conectado na porta: " + portName);
    }

    public static void main(String[] argv) {
        rfidSetup();
        rfidReadPlanSetup();
        arduinoSerialConnect();

        int i = 0;
        try {
            while (true) {
                System.out.println("Fazendo uma nova leitura:");
                rfidRead();
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        }
        reader.destroy();
        arduinoPort.close();
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
            if (DEBUG) e.printStackTrace();
            return;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            if (DEBUG) e.printStackTrace();
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
