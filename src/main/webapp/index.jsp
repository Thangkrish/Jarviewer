<%@ page import = "com.manageengine.wsmfw.apf.common.iamapps.server.amf.event.APFEventsHandler" %>
<%@ page import = "com.manageengine.idmpod.server.management.restapi.util.RestAPIExecutorUtil" %>
<%@ page import = "java.util.*" %>


<html>
    <body>
        Hello World!!


        <%
                    // Java code here
                    int count = 5;

                    long appConfigId = 2000000106124l;
                    long eventId = 2000000058720l;
                    HashMap<Object, Object> properties = new HashMap<>();

                    String callBack = "com.manageengine.wsmfw.apf.common.iamapps.server.amf.callback.APFBatchCallback";
                    String refId = "Check_Proccessing";

                    ArrayList<HashMap<String, Object>> list = new ArrayList<>();

                    HashMap<String, Object> temp = new HashMap<>();
                    temp.put("passwordProfile", "@p)Am0r3");
                    temp.put("displayName", "BatchCheck4");
                    temp.put("UNIQUE_IDENTIFIER", 2000000302246l);
                    temp.put("userPrincipalName", "BatchCheck4@wsmsubdom.406gjx.onmicrosoft.com");
                    temp.put("accountEnabled", "true");
                    temp.put("mailNickname", "BatchCheck4");
                    list.add(temp);

                    temp = new HashMap<>();
                    temp.put("passwordProfile", "@p)Am0r3");
                    temp.put("displayName", "BatchCheck4");
                    temp.put("UNIQUE_IDENTIFIER", 2000000302247l);
                    temp.put("userPrincipalName", "BatchCheck4@wsmsubdom.406gjx.onmicrosoft.com");
                    temp.put("accountEnabled", "true");
                    temp.put("mailNickname", "BatchCheck4");
                    list.add(temp);

                    temp = new HashMap<>();
                    temp.put("passwordProfile", "@p)Am0r3");
                    temp.put("displayName", "BatchCheck4");
                    temp.put("UNIQUE_IDENTIFIER", 2000000302248l);
                    temp.put("userPrincipalName", "BatchCheck4@wsmsubdom.406gjx.onmicrosoft.com");
                    temp.put("accountEnabled", "true");
                    temp.put("mailNickname", "BatchCheck4");

                    list.add(temp);

                    temp = new HashMap<>();
                    temp.put("passwordProfile", "@p)Am0r3");
                    temp.put("displayName", "BatchCheck4");
                    temp.put("UNIQUE_IDENTIFIER", 2000000302249l);
                    temp.put("userPrincipalName", "BatchCheck4@wsmsubdom.406gjx.onmicrosoft.com");
                    temp.put("accountEnabled", "true");
                    temp.put("mailNickname", "BatchCheck4");

                    list.add(temp);

                    properties.put("BULK", list);


                    out.println("List added successfully");

                    ArrayList<HashMap<String, ArrayList<Object>>> eventActionResults = APFEventsHandler.executeEvent(appConfigId, eventId, properties, RestAPIExecutorUtil.getEventCallbackInstance(refId, true, callBack));




    for(int i = 0; i < count; i++) {
%>
    <p>Line number: <%= i + 1 %></p>
<%
    }
%>

    </body>
</html>