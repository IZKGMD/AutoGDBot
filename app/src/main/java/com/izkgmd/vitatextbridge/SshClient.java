package com.izkgmd.vitatextbridge;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** SSH connection with persistent known_hosts. Never disables host-key checking. */
final class SshClient {
    interface TrustPrompt { boolean ask(String question) throws InterruptedException; }

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final File knownHosts;
    private final File keyFile;
    private final TrustPrompt trustPrompt;

    SshClient(String host, int port, String username, String password,
              File knownHosts, File keyFile, TrustPrompt trustPrompt) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.knownHosts = knownHosts;
        this.keyFile = keyFile;
        this.trustPrompt = trustPrompt;
    }

    static String ensurePublicKey(File privateKeyFile) throws Exception {
        File publicKeyFile = new File(privateKeyFile.getAbsolutePath() + ".pub");
        if (!privateKeyFile.exists()) {
            JSch jsch = new JSch();
            KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 3072);
            try {
                try (FileOutputStream secret = new FileOutputStream(privateKeyFile)) {
                    pair.writePrivateKey(secret);
                }
                privateKeyFile.setReadable(false, false);
                privateKeyFile.setReadable(true, true);
                privateKeyFile.setWritable(false, false);
                privateKeyFile.setWritable(true, true);
                try (FileOutputStream pub = new FileOutputStream(publicKeyFile)) {
                    pair.writePublicKey(pub, "VitaTextBridge-Android");
                }
            } finally { pair.dispose(); }
        }
        if (!publicKeyFile.isFile()) throw new IllegalStateException("Не найден .pub для существующего приватного ключа");
        return new String(Files.readAllBytes(publicKeyFile.toPath()), StandardCharsets.UTF_8).trim();
    }

    private Session open() throws Exception {
        if (!knownHosts.exists() && !knownHosts.createNewFile()) {
            throw new IllegalStateException("Не удалось создать хранилище SSH-ключей");
        }
        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHosts.getAbsolutePath());
        if (keyFile.isFile()) jsch.addIdentity(keyFile.getAbsolutePath());
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        Properties settings = new Properties();
        settings.put("StrictHostKeyChecking", "ask");
        settings.put("PreferredAuthentications", "publickey,password,keyboard-interactive");
        settings.put("ConnectTimeout", "12000");
        if (password.isEmpty()) settings.put("MaxAuthTries", "2");
        session.setConfig(settings);
        session.setUserInfo(new AuthInfo());
        try {
            session.connect(12000);
            return session;
        } catch (Exception e) {
            session.disconnect();
            throw e;
        }
    }

    String execute(String command) throws Exception { return execute(command, 45000); }

    String execute(String command, int timeoutMs) throws Exception {
        if (timeoutMs < 1000 || timeoutMs > 300000) throw new IllegalArgumentException("Недопустимый timeout");
        Session session = open();
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getExtInputStream();
            channel.connect(10000);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            final long deadline = System.currentTimeMillis() + timeoutMs;
            int exitCode = -1;
            while (System.currentTimeMillis() < deadline) {
                drain(stdout, output, buf);
                drain(stderr, errors, buf);
                if (channel.isClosed() && stdout.available() == 0 && stderr.available() == 0) {
                    exitCode = channel.getExitStatus();
                    break;
                }
                Thread.sleep(30);
            }
            if (exitCode == -1) throw new IllegalStateException("Превышено время выполнения ("+timeoutMs+" мс)");
            String out = output.toString(StandardCharsets.UTF_8.name());
            String err = errors.toString(StandardCharsets.UTF_8.name());
            if (!err.isEmpty()) out += (out.isEmpty() ? "" : "\n") + "STDERR:\n" + err;
            return out + (out.endsWith("\n") || out.isEmpty() ? "" : "\n") + "[exit " + exitCode + "]";
        } finally {
            if (channel != null) channel.disconnect();
            session.disconnect();
        }
    }

    private static void drain(InputStream in, ByteArrayOutputStream out, byte[] buf) throws Exception {
        while (in.available() > 0) {
            int n = in.read(buf, 0, Math.min(buf.length, in.available()));
            if (n <= 0) return;
            if (out.size() + n <= 131072) out.write(buf, 0, n);
        }
    }
    private final class AuthInfo implements UserInfo, UIKeyboardInteractive {
            @Override public String getPassphrase() { return null; }
            @Override public String getPassword() { return password; }
            @Override public boolean promptPassword(String message) { return !password.isEmpty(); }
            @Override public boolean promptPassphrase(String message) { return false; }
            @Override public void showMessage(String message) { }
            @Override public boolean promptYesNo(String message) {
                // A changed host key MUST NOT be trusted over the previously pinned one.
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("changed") || lower.contains("identification has changed")) return false;
                try {
                    return trustPrompt.ask(message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            @Override public String[] promptKeyboardInteractive(String destination,
                    String name, String instruction, String[] prompt, boolean[] echo) {
                String[] responses = new String[prompt.length];
                for (int i=0; i<prompt.length; i++) responses[i] = password;
                return responses;
            }
    }

}
