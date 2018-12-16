package com.cavalcante.giovanni;

import com.thingmagic.*;
import gnu.io.CommPortIdentifier;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final boolean DEBUG = true;

    private static final int READ_TIME = 3000;
    private static final int TIME_BETWEEN_READS = 10000;
    private static final String URI_SCHEME = "tmr:///";

    private static CommPortIdentifier readerPortId;
    private static CommPortIdentifier arduinoPortId;

    private static Reader reader;
    private static ReadListener readListener = new AddToSetListener();
    private static ReadExceptionListener exceptionListener = new TagReadExceptionReceiver();
    private static ControlePorta arduinoPort;

    private static Set<String> tagsLidas = new HashSet<>();

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
        Enumeration portsEnum = CommPortIdentifier.getPortIdentifiers();
        while (portsEnum.hasMoreElements()) {
            CommPortIdentifier portId = (CommPortIdentifier) portsEnum.nextElement();
            String portName = portId.getName();
            System.out.println("Tentando conectar RFIDReader na porta: " + portName);

            try {
                String readerURI = URI_SCHEME + portName;
                reader = Reader.create(readerURI);
            } catch (ReaderException e) {
                String formattedMessage = "Não foi possível conectar ao Reader com a URI " + portName;
                System.out.println(formattedMessage);
                if (DEBUG) e.printStackTrace();
            }

            ExecutorService executor = Executors.newCachedThreadPool();
                Callable<Object> task = new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        reader.connect();
                        return null;
                    }
                };
                Future<Object> future = executor.submit(task);
            try {
                Object result = future.get(5, TimeUnit.SECONDS);

                readerPortId = portId;
                System.out.println("RFIDReader conectado na porta: " + portName);
                return;
            } catch (Exception e) {
                reader.destroy();
                e.printStackTrace();
            } finally {
                future.cancel(true);
            }
        }
        // Nenhuma das portas conectou
        System.out.println("RFIDReader não conseguiu se conectar");
        reader = null;
    }

    private static void rfidReadPlanSetup() {
        int[] antennaList = {1};

        if (reader == null) {
            return;
        }

        try {
            SimpleReadPlan plan = new SimpleReadPlan(antennaList, TagProtocol.GEN2, null, null, 1000);
            reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, plan);
            reader.addReadListener(readListener);
            reader.addReadExceptionListener(exceptionListener);
        } catch (ReaderException e) {
            String formattedMessage = "Não foi possível criar o plano de leitura para o Reader";
            System.out.println(formattedMessage);
            if (DEBUG) e.printStackTrace();
        }
    }

    private static void rfidRead() {
        TagReadData[] tagReads = null;

        try {
            reader.startReading();
            Thread.sleep(READ_TIME);
            reader.stopReading();
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
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

    private static void arduinoEnviarTagsLidas() throws InterruptedException {
        String msg = "";
        if (tagsLidas.size() > 0) {
            msg += "[";
        }
        for (String tag : tagsLidas) {
            msg += "\"" + tag + "\"" + ",";
            Thread.sleep(1000);
        }
        if (tagsLidas.size() > 0) {
            msg = msg.substring(0, msg.length()-1);
            msg += "]";
        }
        arduinoPort.enviaDados(msg);
        System.out.println("Mensagem enviada: " + msg);
    }

    public static void main(String[] argv) {
        try {
            rfidSetup();
            rfidReadPlanSetup();
            arduinoSerialConnect();

            while (true) {
                System.out.println("Fazendo uma nova leitura:");
                rfidRead();
                arduinoEnviarTagsLidas();
                Thread.sleep(TIME_BETWEEN_READS);
            }
        } catch (InterruptedException e) {
            if (DEBUG) e.printStackTrace();
        } finally {
            if (reader != null) {
                reader.removeReadListener(readListener);
                reader.removeReadExceptionListener(exceptionListener);
                reader.destroy();
            }
            if (arduinoPort != null) {
                arduinoPort.close();
            }
        }
    }

    static class AddToSetListener implements ReadListener
    {
        public void tagRead(Reader r, TagReadData tr) {
            tagsLidas.add(tr.epcString());
        }

    }

    static class TagReadExceptionReceiver implements ReadExceptionListener
    {
        String strDateFormat = "M/d/yyyy h:m:s a";
        SimpleDateFormat sdf = new SimpleDateFormat(strDateFormat);
        public void tagReadException(com.thingmagic.Reader r, ReaderException re)
        {
            String format = sdf.format(Calendar.getInstance().getTime());
            System.out.println("Reader Exception: " + re.getMessage() + " Occured on :" + format);
            if(re.getMessage().equals("Connection Lost"))
            {
                System.exit(1);
            }
        }
    }

// ENVIO DE INFORMAÇÕES VIA HTTP - FEITO NO ARDUINO
//    static void sendHTTPRequest(List<String> tagIDs) {
//        HttpURLConnection con = null;
//        try {
//            URL url = new URL("http://52.52.244.199:80/");
//            con = (HttpURLConnection) url.openConnection();
//            con.setRequestMethod("POST");
//
//            Map<String, String> parameters = new HashMap<>();
//            parameters.put("tags", tagIDs.toString());
//
//            con.setDoOutput(true);
//            DataOutputStream out = new DataOutputStream(con.getOutputStream());
//            out.writeBytes(getParamsString(parameters));
//            out.flush();
//            out.close();
//
//            con.connect();
//
//        } catch (MalformedURLException e) {
//            // TODO Auto-generated catch block
//            if (DEBUG) e.printStackTrace();
//            return;
//        } catch (IOException e) {
//            // TODO Auto-generated catch block
//            if (DEBUG) e.printStackTrace();
//            return;
//        } finally {
//            if (con != null) {
//                con.disconnect();
//            }
//        }
//    }
//
//    static String getParamsString(Map<String, String> params) throws UnsupportedEncodingException {
//        StringBuilder result = new StringBuilder();
//
//        for (Map.Entry<String, String> entry : params.entrySet()) {
//            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
//            result.append("=");
//            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
//            result.append("&");
//        }
//
//        String resultString = result.toString();
//        return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
//    }

}
