/*
 * Copyright 2018 Everlaw
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package netbeanstypescript;

import java.io.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.logging.Level;
import static netbeanstypescript.TSService.*;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.openide.modules.InstalledFileLocator;

/**
 *
 * @author jeffrey
 */
public class TSServiceProcess {

    private Process process;
    private OutputStream stdin;
    private BufferedReader stdout;
    private InputStream stderr;
    private String commError;
    private boolean threadStarted;
    private volatile String procError;
    private int configGen;
    private String configError;

    public TSServiceProcess() {
        File file = InstalledFileLocator.getDefault().locate("nbts-services.js", "netbeanstypescript", false);
        if (file == null) {
            commError = "Plugin installation problem: nbts-services.js missing";
            return;
        }
        PROCESS: {
            StringBuilder failedAttempts = new StringBuilder();
            // Node installs to /usr/local/bin on OS X, but OS X doesn't put /usr/local/bin in the
            // PATH of applications started from the GUI
            for (String command: new String[] { "nodejs", "node", "/usr/local/bin/node" }) {
                try {
                    String[] args = { command, "--harmony", file.toString() };
                    process = new ProcessBuilder().command(args).start();
                    log.log(Level.INFO, "Started process: {0}", String.join(" ", args));
                    break PROCESS;
                } catch (IOException e) {
                    failedAttempts.append('\n').append(e);
                }
            }
            commError = "Error creating Node.js process:" + failedAttempts +
                    "\n\nMake sure the \"nodejs\" or \"node\" executable is installed and on your PATH.";
            return;
        }
        stdin = process.getOutputStream();
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));
        stderr = process.getErrorStream();
    }

    private static void stringToJS(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20) {
                sb.append("\\u");
                for (int j = 12; j >= 0; j -= 4) {
                    sb.append("0123456789ABCDEF".charAt((c >> j) & 0x0F));
                }
            } else {
                if (c == '\\' || c == '"') {
                    sb.append('\\');
                }
                sb.append(c);
            }
        }
        sb.append('"');
    }

    public Object call(String funcName, Object... args) {
        if (commError != null) {
            return TSException.class;
        }
        StringBuilder sb = new StringBuilder(funcName).append('(');
        for (Object arg: args) {
            if (sb.charAt(sb.length() - 1) != '(') sb.append(',');
            if (arg instanceof CharSequence) {
                stringToJS(sb, (CharSequence) arg);
            } else {
                sb.append(arg);
            }
        }
        String code = sb.append(")\n").toString();
        if (! threadStarted) {
            new ErrorCaptureThread().start();
            threadStarted = true;
        }
        log.log(Level.FINER, "OUT[{0}]: {1}", new Object[] {
            code.length(), code.length() > 120 ? code.substring(0, 120) + "...\n" : code});
        long t1 = System.currentTimeMillis();
        String received = null;
        try {
            if (stdout.ready()) throw new IOException("Unexpected data on stdout");
            stdin.write(code.getBytes(UTF_8));
            stdin.flush();
            for (String s; (s = stdout.readLine()) != null; ) {
                if (! s.isEmpty() && s.charAt(0) == 'L') {
                    log.fine(String.valueOf(JSONValue.parseWithException(s.substring(1))));
                    continue;
                }
                received = s.length() > 120 ? s.substring(0, 120) + "..." : s;
                log.log(Level.FINER, "IN[{0},{1}]: {2}\n", new Object[] {
                    s.length(), System.currentTimeMillis() - t1, received });
                return JSONValue.parseWithException(s);
            }
            throw new EOFException();
        } catch (IOException | ParseException e) {
            commError = "Error communicating with nbts-services\n"
                    + (received != null ? "Received: " + received + "\n" : "")
                    + e;
            return TSException.class;
        }
    }

    public Object query(Object... filenameAndArgs) throws TSException {
        if (configGen < TSPluginConfig.configGen) {
            configGen = TSPluginConfig.configGen;
            String libDir = TSPluginConfig.getLibDir();
            if (libDir.isEmpty()) {
                configError = "TypeScript lib directory not set";
            } else {
                Object res = call("configure", libDir, TSPluginConfig.getLocale());
                configError = res instanceof String
                        ? "Failed to load TypeScript from " + libDir + "\n\n" + res
                        : null;
            }
        }
        if (configError != null) {
            throw new TSException(configError + "\n\nPlease check plugin configuration (context menu > \"TypeScript Setup...\")");
        }
        Object res = call("query", filenameAndArgs);
        if (res == TSException.class) {
            throw new TSException((procError != null ? procError : commError)
                + "\n\nClose project and reopen to retry.");
        } else if (res instanceof String) {
            log.log(Level.WARNING, "Caught exception in JS: {0}", res);
            throw new TSException("Caught exception in JS: " + (String) res);
        }
        return res;
    }

    public boolean isValid() {
        return commError == null;
    }

    private class ErrorCaptureThread extends Thread {
        @Override
        public void run() {
            byte[] errBuf = new byte[1000];
            int pos = 0;
            String exitStatus;
            try {
                for (int len; (len = stderr.read(errBuf, pos, errBuf.length - pos)) > 0; ) {
                    pos += len;
                }
                stderr.close();
                exitStatus = "[Exit status " + process.waitFor() + "]";
            } catch (IOException | InterruptedException e) {
                exitStatus = "[" + e.toString() + "]";
            }
            procError = "Error in nbts-services process:\n" +
                    new String(errBuf, 0, pos, UTF_8) + exitStatus;
        }
    }

    public void close() {
        if (process != null) process.destroy(); // Closes all streams
    }
}
