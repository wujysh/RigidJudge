package team.dhuacm.RigidJudge.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.Source;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import team.dhuacm.RigidJudge.config.*;
import team.dhuacm.RigidJudge.model.Solution;

/**
 * Created by wujy on 15-1-10.
 */
public class Query {

    public final static int QUERY_COUNT = 5;

    public static void doQuery(CloseableHttpClient client, OJProperty ojProperty, OJAccount ojAccount, Solution solution) throws JudgeException, NetworkException {

        for (int i = 0; i < DataProvider.Remote_QueryInterval.size(); i++) {
            long sleepTime = DataProvider.Remote_QueryInterval.get(i) * 1000;
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName() + " - try " + i + " times!");

            getResult(client, ojProperty, ojAccount, solution);

            if (Result.Queue == solution.getResult()) {
                if ((i + 1) == DataProvider.Remote_QueryInterval.size()) {
                    solution.setResult(Result.Other_Error);
                } else
                    continue;
            } else
                break;
        }
    }

    //get result and other info form query page
    public static void getResult(CloseableHttpClient client, OJProperty ojProperty, OJAccount ojAccount, Solution solution) throws JudgeException, NetworkException {

        URI uri = URI.create(ojProperty.getQueryUrl());
        String queryUsername = ojProperty.getQueryUsername();
        if (null != queryUsername && !"".equals(queryUsername)) {
            try {
                uri = new URIBuilder(uri).addParameter(queryUsername, ojAccount.getUsername()).build();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        //System.out.println(uri);

        HttpGet get = new HttpGet(uri);
        CloseableHttpResponse response = null;
        try {
            response = client.execute(get);
            if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode()) {
                solution.setResult(Result.Other_Error);
                return;
            }
            HttpEntity entity = response.getEntity();
            if (null == entity) {
                return;
            }
            String html = EntityUtils.toString(entity, ojProperty.getOjCharset());
            Source source = new Source(html);
            if (solution.getOj() == OJ.UVALIVE) {
                source = new Source(source.getAllElementsByClass("maincontent").toString());
            }
            source.setLogger(null);
            List<Element> tableElements = source.getAllElements("table");
            for (Element tableElement : tableElements) {
                List<Element> trElements = tableElement.getAllElements("tr");
                if (trElements.size() >= 2) {
                    List<Element> tdElements = trElements.get(1).getAllElements("td");
                    if (tdElements.size() == ojProperty.getQueryTableColumns()) {
                        Element tdElement = tdElements.get(ojProperty.getQueryResultColumn() - 1);
                        String resultStr = tdElement.getTextExtractor().toString();
                        //System.out.println(resultStr);
                        String[] results = ojProperty.getOjResults();
                        Result result = null;
                        for (int i = 0; i < results.length; i++) {
                            if (-1 != resultStr.toLowerCase().indexOf(results[i].toLowerCase())) {
                                result = Result.values()[i];
                                break;
                            }
                        }
                        //no result match
                        if (null == result)
                            return;
                        solution.setResult(result);
                        if (result == Result.Accept) {
                            if (ojProperty.getQueryMemoryColumn() != 0) {
                                Element memoryTdElement = tdElements.get(ojProperty.getQueryMemoryColumn() - 1);
                                String memoryStr = memoryTdElement.getTextExtractor().toString();
                                memoryStr = memoryStr.replace(ojProperty.getQueryMemoryUnit(), "");
                                int memory = Integer.parseInt(memoryStr);
                                solution.setMemory(memory);
                            }
                            if (ojProperty.getQueryRuntimeColumn() != 0) {
                                Element runtimeTdElement = tdElements.get(ojProperty.getQueryRuntimeColumn() - 1);
                                String runtimeStr = runtimeTdElement.getTextExtractor().toString();
                                runtimeStr = runtimeStr.replace(ojProperty.getQueryRuntimeUnit(), "");
                                //System.out.println(runtimeStr);
                                int runtime = 0;
                                if (ojProperty.getQueryRuntimeUnit().equalsIgnoreCase("S")) {
                                    runtime = (int) (Double.parseDouble(runtimeStr) * 1000);
                                } else {
                                    runtime = Integer.parseInt(runtimeStr);
                                }
                                solution.setTime(runtime);
                            }
                        }
                    } else
                        continue;
                } else
                    continue;
            }

        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != response)
                    response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            get.releaseConnection();
        }

    }
}
