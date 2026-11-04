package com.izkgmd.vitatextbridge;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
    private final TrustPrompt trustPrompt;

    SshClient(String host, int port, String username, String password,
              File knownHosts, TrustPrompt trustPrompt) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.knownHosts = knownHosts;
        this.trustPrompt = trustPrompt;
    }

    private Session open() throws Exception {
        if (!knownHosts.exists() && !knownHosts.createNewFile()) {
            throw new IllegalStateException("Не удалось создать хранилище SSH-ключей");
        }
        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHosts.getAbsolutePath());
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        Properties settings = new Properties();
        settings.put("StrictHostKeyChecking", "ask");
        settings.put("PreferredAuthentications", "password,keyboard-interactive");
        settings.put("ConnectTimeout", "12000");
        session.setConfig(settings);
        session.setUserInfo(new UIKeyboardInteractive() {
            @Override public String getPassphrase() { return null; }
            @Override public String getPassword() { return password; }
            @Override public boolean promptPassword(String message) { return true; }
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
        });
        try {
            session.connect(12000);
            return session;
        } catch (Exception e) {
            session.disconnect();
            throw e;
        }
    }

    String execute(String command) throws Exception {
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
            final long deadline = System.currentTimeMillis() + 45000;
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
            if (exitCode == -1) throw new IllegalStateException("Превышено время выполнения (45 секунд)");
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
}
