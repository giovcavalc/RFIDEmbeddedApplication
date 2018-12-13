package com.cavalcante.giovanni;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.SerialPort;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ControlePorta {
    private InputStream serialIn;
    private OutputStream serialOut;
    private int taxa;
    private String portaCOM;

    /**
     * Construtor da classe ControlePorta
     * @param portaCOM - Porta COM que será utilizada para enviar os dados para o arduino
     * @param taxa - Taxa de transferência da porta serial geralmente é 9600
     */
    public ControlePorta(String portaCOM, int taxa) {
        this.portaCOM = portaCOM;
        this.taxa = taxa;
        this.initialize();
    }

    /**
     * Método que verifica se a comunicação com a porta serial está ok
     */
    private void initialize() {
        try {
            //Define uma variável portId do tipo CommPortIdentifier para realizar a comunicação serial
            CommPortIdentifier portId = null;

            try {
                //Tenta verificar se a porta COM informada existe
                portId = CommPortIdentifier.getPortIdentifier(this.portaCOM);
            }catch (NoSuchPortException npe) {
                //Caso a porta COM não exista será exibido um erro
                System.out.println("Porta COM não encontrada.");
            }
            //Abre a porta COM
            SerialPort port = (SerialPort) portId.open("Comunicação serial", this.taxa);
            serialIn = port.getInputStream();
            serialOut = port.getOutputStream();
            port.setSerialPortParams(this.taxa, //taxa de transferência da porta serial
                    SerialPort.DATABITS_8, //taxa de 10 bits 8 (envio)
                    SerialPort.STOPBITS_1, //taxa de 10 bits 1 (recebimento)
                    SerialPort.PARITY_NONE); //receber e enviar dados
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Método que fecha a comunicação com a porta serial
     */
    public void close() {
        try {
            serialOut.close();
        }catch (IOException e) {
            System.out.println("Não foi possível fechar a porta serial");
        }
    }

    public String recebeDados() {
        String s = null;
        try {
            s = IOUtils.toString(serialIn, UTF_8.name());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s;
    }

    /**
     * @param dado - Valor a ser enviado pela porta serial
     */
    public void enviaDados(String dado){
        try {
            serialOut.write(dado.getBytes()); //escreve o valor na porta serial para ser enviado
        } catch (IOException ex) {
            System.out.println("Erro ao enviar os dados pela porta serial");
        }
    }
}
