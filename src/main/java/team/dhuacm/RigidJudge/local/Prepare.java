package team.dhuacm.RigidJudge.local;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.IOUtils;
import team.dhuacm.RigidJudge.config.DataProvider;
import team.dhuacm.RigidJudge.config.Language;
import team.dhuacm.RigidJudge.config.Result;
import team.dhuacm.RigidJudge.exception.JudgeException;
import team.dhuacm.RigidJudge.exception.NetworkException;
import team.dhuacm.RigidJudge.model.LocalProblem;
import team.dhuacm.RigidJudge.model.LocalSpecialProblem;
import team.dhuacm.RigidJudge.model.Solution;
import team.dhuacm.RigidJudge.utils.FileUtils;
import team.dhuacm.RigidJudge.utils.HttpClientUtil;

import javax.xml.crypto.Data;
import java.io.*;

/**
 * Created by wujy on 15-1-18.
 */
class Prepare {

    private static final Logger logger = LoggerFactory.getLogger(Prepare.class.getSimpleName());
    private static CloseableHttpClient httpClient = null;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static boolean doPrepare(Solution solution) throws IOException {

        LocalProblem problem = (LocalProblem) solution.getProblem();

        try {
            prepareData(problem);
        } catch (Exception e) {
            logger.error(null, e);
            return false;
        }

        solution.setInput(FileUtils.getFileContent(new File("data/" + problem.getInputFileName().substring(problem.getInputFileName().lastIndexOf("/") + 1))));
        solution.setStdAns(FileUtils.getFileContent(new File("data/" + problem.getOutputFileName().substring(problem.getOutputFileName().lastIndexOf("/") + 1))));
        if (problem.getTimeLimit().containsKey(solution.getLanguage())) {
            solution.setTimeLimit(problem.getTimeLimit().get(solution.getLanguage()));
        } else {
            solution.setTimeLimit(problem.getTimeLimit().get(Language.DEFAULT));
        }
        if (problem.getMemoryLimit().containsKey(solution.getLanguage())) {
            solution.setMemoryLimit(problem.getMemoryLimit().get(solution.getLanguage()));
        } else {
            solution.setMemoryLimit(problem.getMemoryLimit().get(Language.DEFAULT));
        }
        //System.out.println(solution.getInput());
        //System.out.println(solution.getStdAns());

        File file = new File("tmp");
        if (!file.exists()) {
            file.mkdir();
        }
        if (solution.getLanguage().equals(Language.C)) {  // Temporarily, will change to DataProvider
            file = new File("tmp/test.c");
        } else if (solution.getLanguage().equals(Language.CPP)) {
            file = new File("tmp/test.cpp");
        } else {
            file = new File("tmp/test.java");
        }
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(solution.getCode());
        writer.close();

        // Prepare for Special judge, copy input/output data and pre-compile spj
        if (problem instanceof LocalSpecialProblem) {
            FileUtils.fileTransferCopy(new File("data/" + problem.getInputFileName().substring(problem.getInputFileName().lastIndexOf("/") + 1)), new File("tmp/test.in"));
            FileUtils.fileTransferCopy(new File("data/" + problem.getOutputFileName().substring(problem.getOutputFileName().lastIndexOf("/") + 1)), new File("tmp/test.out"));

            if (((LocalSpecialProblem) problem).getJudgerProgramLanguage().equals(Language.C)) {  // Temporarily, will change to DataProvider
                file = new File("tmp/spj.c");
            } else if (((LocalSpecialProblem) problem).getJudgerProgramLanguage().equals(Language.CPP)) {
                file = new File("tmp/spj.cpp");
            } else {
                file = new File("tmp/spj.java");
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(((LocalSpecialProblem) problem).getJudgerProgramCode());
            writer.close();

            if (!Compile.doCompile(((LocalSpecialProblem) problem).getJudgerProgramLanguage(), "tmp/spj", "tmp/spj")) {
                logger.error("SPJ compile error!");
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void prepareData(LocalProblem problem) throws JudgeException, NetworkException {
        File file;
        file = new File("data");
        if (!file.exists()) {
            file.mkdir();
        }

        String dataServerPrefix = "http://" + DataProvider.Local_DataServerHost + ":" + DataProvider.Local_DataServerPort;
        downloadIfNotExists("data/" + problem.getInputFileName().substring(problem.getInputFileName().lastIndexOf("/") + 1), dataServerPrefix + problem.getInputFileName());
        downloadIfNotExists("data/" + problem.getOutputFileName().substring(problem.getOutputFileName().lastIndexOf("/") + 1), dataServerPrefix + problem.getOutputFileName());
    }

    private static void downloadIfNotExists(String filename, String url) throws JudgeException, NetworkException {
        File file = new File(filename);
        if (!file.exists()) {
            // download from data server
            HttpGet get = new HttpGet(url);
            CloseableHttpResponse response = null;
            try {
                response = httpClient.execute(get);
                HttpEntity entity = response.getEntity();
                BufferedInputStream bis = new BufferedInputStream(entity.getContent());
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(new File(filename)));
                int inByte;
                while ((inByte = bis.read()) != -1) {
                    bos.write(inByte);
                }
                bis.close();
                bos.close();
                logger.info("Downloaded '{}'", filename);
            } catch (ClientProtocolException e) {
                throw new JudgeException(e.getMessage(), e.getCause());
            } catch (IOException e) {
                throw new NetworkException(e.getMessage(), e.getCause());
            } finally {
                try {
                    if (null != response)
                        response.close();
                } catch (IOException e) {
                    logger.error(null, e);
                }
                get.releaseConnection();
            }
        } else {
            logger.info("Already cached '{}'", filename);
        }
    }

    static {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(DataProvider.Local_DataServerHost, DataProvider.Local_DataServerPort),
                new UsernamePasswordCredentials(DataProvider.Local_DataServerUsername, DataProvider.Local_DataServerPassword));
        httpClient = HttpClientUtil.get(DataProvider.Remote_RetryTimes, DataProvider.Remote_SocketTimeout, DataProvider.Remote_ConnectionTimeout, credentialsProvider);
    }
}
