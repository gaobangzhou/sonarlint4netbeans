package com.github.philippefichet.sonarlint4netbeans.tools;

import com.github.philippefichet.sonarlint4netbeans.option.SonarRulesPage;
import com.google.gson.Gson;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HttpURLConnectionUtil {

    public static final String SONAR_RULES_SEARCH_URL = "/api/rules/search?p=1&ps=400&asc=true&qprofile={profileId}&activation=true&f=params,name,lang";

    public static final String SONAR_PROFILE_URL = "/api/qualityprofiles/search?qualityProfile={profileName}";

    public static final String SONAR_SERVER_VERSION = "/api/server/version";

    public static final String HTTP_SUCCESS = "success";

    public static final String HTTP_DATA = "data";

    /**
     * http get请求
     *
     * @param httpUrl 链接
     * @return 响应数据
     */
    public static Map<String, String> doGet(String httpUrl) {
        //链接
        Map<String, String> resultMap = new HashMap<>();
        HttpURLConnection connection = null;
        InputStream is = null;
        BufferedReader br = null;
        StringBuffer result = new StringBuffer();
        try {
            //创建连接
            URL url = new URL(httpUrl);
            connection = (HttpURLConnection) url.openConnection();
            //设置请求方式
            connection.setRequestMethod("GET");
            //设置连接超时时间
            connection.setConnectTimeout(15000);
            //设置读取超时时间
            connection.setReadTimeout(15000);
            //开始连接
            connection.connect();
            //获取响应数据
            if (connection.getResponseCode() == 200) {
                //获取返回的数据
                is = connection.getInputStream();
                if (is != null) {
                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String temp = null;
                    while ((temp = br.readLine()) != null) {
                        result.append(temp);
                    }
                }
                resultMap.put(HTTP_SUCCESS, "true");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            resultMap.put(HTTP_SUCCESS, "false");
        } catch (IOException e) {
            resultMap.put(HTTP_SUCCESS, "false");
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                connection.disconnect();// 关闭远程连接
            }
        }
        resultMap.put(HTTP_DATA, result.toString());
        return resultMap;
    }

    /**
     * post请求
     *
     * @param httpUrl 链接
     * @param param 参数
     * @return
     */
    public static String doPost(String httpUrl, String param) {
        StringBuffer result = new StringBuffer();
        //连接
        HttpURLConnection connection = null;
        OutputStream os = null;
        InputStream is = null;
        BufferedReader br = null;
        try {
            //创建连接对象
            URL url = new URL(httpUrl);
            //创建连接
            connection = (HttpURLConnection) url.openConnection();
            //设置请求方法
            connection.setRequestMethod("POST");
            //设置连接超时时间
            connection.setConnectTimeout(15000);
            //设置读取超时时间
            connection.setReadTimeout(15000);
            //设置是否可读取
            connection.setDoOutput(true);
            //设置响应是否可读取
            connection.setDoInput(true);
            //设置参数类型
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //拼装参数
            if (param != null && !param.equals("")) {
                //设置参数
                os = connection.getOutputStream();
                //拼装参数
                os.write(param.getBytes("UTF-8"));
            }
            //设置权限
            //设置请求头等
            //开启连接
            //读取响应
            if (connection.getResponseCode() == 200) {
                is = connection.getInputStream();
                if (is != null) {
                    br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String temp = null;
                    if ((temp = br.readLine()) != null) {
                        result.append(temp);
                    }
                }
            }
            //关闭连接
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (connection != null) {
                //关闭连接
                connection.disconnect();
            }

        }
        return result.toString();
    }
    
    public static void main(String[] args) {
        Gson gson = new Gson();
        Map<String, String> result = HttpURLConnectionUtil.doGet("http://10.170.190.54:9000"
                + HttpURLConnectionUtil.SONAR_RULES_SEARCH_URL.replace("{profileId}", "AXcdXoDXm5rVA-yHOvHX"));
        if (Boolean.valueOf(result.get(HttpURLConnectionUtil.HTTP_SUCCESS)) && !result.get(HttpURLConnectionUtil.HTTP_DATA).isEmpty()) {
            String rulesString = result.get(HttpURLConnectionUtil.HTTP_DATA);
            SonarRulesPage rulesPage = gson.fromJson(rulesString, SonarRulesPage.class);
            System.out.println(rulesPage.getRules().size());
        }
    }
}
