package team.dhuacm.RigidJudge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.dhuacm.RigidJudge.model.Solution;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by wujy on 15-1-7.
 */
public class DataProvider {

    private final static Logger logger = LoggerFactory.getLogger(DataProvider.class.getSimpleName());

    // Common configurations
    public static String RabbitMQ_Host;
    public static int RabbitMQ_Port;
    public final static HashMap<OJ, OJProperty> OJs = new HashMap<OJ, OJProperty>();

    // Local judge configurations
    public static boolean Local_RunInSandbox = true;
    public static int Local_CompileTimeLimit = 0;
    public static int Local_OutputLengthLimit = 0;
    public final static Map<Language, String> Local_CompileCommand = new HashMap<Language, String>();
    public final static Map<Language, String> Local_RunCommand = new HashMap<Language, String>();

    // Remote judge configurations
    public final static List<Integer> Remote_QueryInterval = new ArrayList<Integer>();
    public static int Remote_Concurrency = 0;
    public static int Remote_RetryTimes = 0;
    public static int Remote_SocketTimeout = 0;
    public static int Remote_ConnectionTimeout = 0;
    public final static HashMap<OJ, BlockingQueue<OJAccount>> Remote_OJAccounts = new HashMap<OJ, BlockingQueue<OJAccount>>();

    // Common data structure
    public final static LinkedBlockingQueue<Solution> JudgedSolutionQueue = new LinkedBlockingQueue<Solution>();

    static {
        Properties p = new Properties();
        try {
            p.load(new FileInputStream("configs/Config.properties"));
        } catch (FileNotFoundException e) {
            logger.error("Fatal Error: Cannot find the file: configs/Config.properties", e);
            System.exit(1);
        } catch (IOException e) {
            logger.error(null, e);
        }

        RabbitMQ_Host = p.getProperty("RabbitMQ_Host", "127.0.0.1");
        RabbitMQ_Port = Integer.parseInt(p.getProperty("RabbitMQ_Port", "5672"));

        Local_RunInSandbox = Boolean.parseBoolean(p.getProperty("Local_RunInSandbox", "true"));
        Local_CompileTimeLimit = Integer.parseInt(p.getProperty("Local_CompileTimeLimit", "5"));
        Local_OutputLengthLimit = Integer.parseInt(p.getProperty("Local_OutputLengthLimit", "5242880"));

        Remote_Concurrency = Integer.parseInt(p.getProperty("Remote_Concurrency", "10"));
        Remote_RetryTimes = Integer.parseInt(p.getProperty("Remote_RetryTimes", "3"));
        Remote_SocketTimeout = Integer.parseInt(p.getProperty("Remote_SocketTimeout", "30"));
        Remote_ConnectionTimeout = Integer.parseInt(p.getProperty("Remote_ConnectionTimeout", "30"));
        for (String str : p.getProperty("Remote_QueryInterval").split(",")) {
            Remote_QueryInterval.add(Integer.parseInt(str));
        }


        logger.info("RabbitMQ Server: {}:{}", RabbitMQ_Host, RabbitMQ_Port);
        logger.info("[Local] Run in Sandbox: {}, Compile Time Limit: {}", Local_RunInSandbox, Local_CompileTimeLimit);

        try {
            p.load(new FileInputStream("configs/local/compile.properties"));
        } catch (FileNotFoundException e) {
            logger.error("Fatal Error: Cannot find the file: configs/local/compile.properties", e);
            System.exit(1);
        } catch (IOException e) {
            logger.error(null, e);
        }
        for (Language l : Language.values()) {
            String command = p.getProperty(l.name().toLowerCase());
            Local_CompileCommand.put(l, command);
            logger.info("        {} - '{}'", l.name(), command);
        }
        logger.info("Init local/compile.properties Config OK!");

        try {
            p.load(new FileInputStream("configs/local/run.properties"));
        } catch (FileNotFoundException e) {
            logger.error("Fatal Error: Cannot find the file: configs/local/run.properties", e);
            System.exit(1);
        } catch (IOException e) {
            logger.error(null, e);
        }
        for (Language l : Language.values()) {
            String command = p.getProperty(l.name().toLowerCase());
            Local_RunCommand.put(l, command);
            logger.info("        {} - '{}'", l.name(), command);
        }
        logger.info("Init local/run.properties Config OK!");


        logger.info("[Remote] Retry Num: {}, Socket Timeout: {}, Connection Timeout: {}", Remote_RetryTimes, Remote_SocketTimeout, Remote_ConnectionTimeout);
        logger.info("         Query Time: {}", Remote_QueryInterval);

        // Remote OJ Accounts
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File("configs/remote/OJAccounts.properties"));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    String[] strs = line.split(",");
                    String ojName = strs[0].trim().toUpperCase();
                    OJ oj = OJ.valueOf(ojName);
                    OJAccount account = new OJAccount(strs[1].trim(), strs[2].trim());
                    BlockingQueue<OJAccount> queue = Remote_OJAccounts.get(oj);
                    if (null == queue) {
                        queue = new LinkedBlockingQueue<OJAccount>();
                        Remote_OJAccounts.put(oj, queue);
                    }
                    queue.put(account);
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Fatal Error: Cannot find the file: remote/OJAccounts.properties", e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.error(null, e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        for (OJ oj : Remote_OJAccounts.keySet()) {
            logger.info("         {} - {}", oj, Remote_OJAccounts.get(oj).size());
        }
        logger.info("Init remote/OJAccounts.properties Config OK!");

        // OJs
        File file = new File("configs/remote/OJProperty");
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                if (f.getName().endsWith(".properties")) {
                    OJProperty ojProperty = new OJProperty(f);
                    OJs.put(OJ.valueOf(ojProperty.getOjName().toUpperCase()), ojProperty);
                }
            }
        }
        logger.info("Init remote/OJProperty Config OK!");
    }
}
