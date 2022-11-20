package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.spi.operations.*;
import org.identityconnectors.common.security.SecurityUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ConnectorClass(displayNameKey = "connector.display", configurationClass = RCLConfiguration.class)
public class RCLConnector implements Connector, AuthenticateOp, CreateOp, DeleteOp, UpdateOp, SchemaOp, SearchOp<Filter>, TestOp {

    public Configuration getConfiguration() {
        return _configuration;
    }

    public void init(Configuration configuration) {
        this._configuration = (RCLConfiguration) configuration;
        idmHost = _configuration.getIdmHost();
        idmPort = _configuration.getIdmPort();
        idmUserId = _configuration.getIdmUserName();
        idmUserPassword = _configuration.getIdmPassword();
        idmUserFilter = _configuration.getIdmUserFilter();
        httpClient = HttpClientBuilder.create().build();
        //System.out.println(" IDM host "+ idmHost);
        //System.out.println(" IDM Port "+ idmPort);
        //System.out.println(" IDM User ID "+ idmUserId);
        //System.out.println(" IDM Password "+ SecurityUtil.decrypt(idmUserPassword));
    }


    public void dispose() {
        if(httpClient != null)
            httpClient = null;
    }

    @Override
    public Uid authenticate(ObjectClass objectClass, String s, GuardedString guardedString, OperationOptions operationOptions) {
        httpClient = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet(idmHost+":"+idmPort+"/openidm/managed/user?_queryFilter=username%20sw%20%22"+idmUserId+"%22");
        httpGet.setHeader("X-OpenIDM-Username", idmUserId);
        httpGet.setHeader("X-OpenIDM-Password", SecurityUtil.decrypt(idmUserPassword));
        httpGet.setHeader("Accept-API-Version", "resource=1.0");
        httpGet.setHeader("Content-Type", "application/json");
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
            int sc = httpResponse.getStatusLine().getStatusCode();
            if(sc == HttpStatus.SC_OK) {
               HttpEntity httpEntity = httpResponse.getEntity();
                if (httpEntity != null) {
                    // return it as a String
                    String result = EntityUtils.toString(httpEntity);
                    System.out.println(result);
                    ObjectMapper map = new ObjectMapper();
                    JsonNode node = map.readTree(result);
                    String uid = node.get("_id").textValue();
                    return new Uid(uid);
                }

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Uid create(ObjectClass objectClass, Set<Attribute> set, OperationOptions operationOptions) {
        System.out.println(" I am in create ");
        return null;
    }

    @Override
    public Uid update(ObjectClass objectClass, Uid uid, Set<Attribute> set, OperationOptions operationOptions) {
        System.out.println(" ###################### Entering update ###########################");
        System.out.println(uid.getUidValue());

        List<Attribute> groupList = null;
        groupList = set.stream()
                .filter(attr -> (attr.getName().equals("roles")))
                .collect(Collectors.toList());

        if(groupList.size() > 0) {
            List<Object> userGroups = groupList.get(0).getValue();
            //System.out.println("Size of groups: "+ userGroups.size());
            if(userGroups.size() > 0) {
                System.out.println(" User "+ uid.getUidValue() +" has "+userGroups.size() +" groups ###########################");
            } else { System.out.println(" No groups were sent ");}
            handleRoleMemberships(uid.getUidValue(),userGroups);
        }
        System.out.println(" ###################### Leaving update ###########################");
        return uid;
    }

    @Override
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions operationOptions) {
        System.out.println(" I am in delete ");
    }

    @Override
    public Schema schema() {
        final SchemaBuilder schemaBuilder = new SchemaBuilder(RCLConnector.class);
        schemaBuilder.defineObjectClass(getUserObjectClassInfo());
        schemaBuilder.defineObjectClass(getGroupObjectClassInfo());
        // Operation Options
       schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(),
                SearchOp.class);
        // Support paged Search
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildPageSize(),
                SearchOp.class);
        schemaBuilder.defineOperationOption(OperationOptionInfoBuilder.buildPagedResultsCookie(),
                SearchOp.class);

        return schemaBuilder.build();
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions operationOptions) {
        //System.out.println(" I am in Filter Translator ");
        Map<String,Object> options = operationOptions.getOptions();
        //for (Map.Entry<String,Object> entry : options.entrySet())
        //    System.out.println("Key = " + entry.getKey() + ")" );
                    //", Value = " + entry.getValue());

        return new FilterTranslator<Filter>() {
            @Override
            public List<Filter> translate(Filter filter) {
                return CollectionUtil.newList(filter);
            }
        };
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter filter, ResultsHandler resultsHandler, OperationOptions operationOptions) {
        //System.out.println(" Calling executeQuery ");
        boolean isGet = false;
        String s = null;
        ObjectMapper map = new ObjectMapper();

        Uid uuid = null;
        if(null != filter) {
            uuid = FrameworkUtil.getUidIfGetOperation(filter);
            isGet = true;
        }

        httpClient = HttpClientBuilder.create().build();

        if (ObjectClass.ACCOUNT.equals(objectClass)) {

            if(null != filter ){
                if(filter.toString().contains("STARTSWITH")) {
                    StartsWithFilter f = (StartsWithFilter) filter;
                    s = f.getAttribute().getName()+"sw%20%22"+f.getValue()+"%22";
                }
            }
            if(null != s) {
                userStr = idmHost+":" + idmPort +"/openidm/managed/user?_queryFilter"+s;
            } else {
                if (null != uuid) {
                    userStr = idmHost+":" + idmPort +"/openidm/managed/user/"+uuid.getUidValue();
                } else {
                    if(null != idmUserFilter) {
                        userStr = idmHost + ":" + idmPort + "/openidm/managed/user?_queryFilter="+ URLEncoder.encode(idmUserFilter);
                    } else {
                        userStr = idmHost + ":" + idmPort + "/openidm/managed/user?_queryFilter=true";
                    }
                }
            }

            String res = null;
            JsonNode node = null;
            JsonNode result = null;
            ConnectorObject connectorObject = null;
            int _pagedResultsOffset = 0;

            try {
                _pagedResultsOffset = operationOptions.getPagedResultsOffset();
            } catch (Exception e) {_pagedResultsOffset = 0;}

            if(_pagedResultsOffset > 0) {
                _pageCookie = null;
            } else {
                //System.out.println(" Will perform cookie-based search ");
            }
            if(isGet) {
                //System.out.println(" GET: "+ userStr);
                try {
                    res = getObject(userStr);
                    if(null != res) {
                        node = map.readTree(res);
                        connectorObject = buildUserObject(node, objectClass);
                        resultsHandler.handle(connectorObject);
                    } else {
                        System.out.println(" Null ");
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                if(null != operationOptions.getPageSize()){
                    _pageSize = operationOptions.getPageSize();
                } else {
                    _pageSize = 50;
                }
                userStr = userStr + "&_totalPagedResultsPolicy=EXACT";
                String qry = null;
                try {
                    if( _pagedResultsOffset > 0){
                        _pageCookie = null;
                        qry = userStr +"&_pageSize="+_pageSize+"&_pagedResultsOffset="+_pagedResultsOffset;
                        //System.out.println(" URL with Offset: " + qry);
                    } else {
                        qry = userStr +"&_pageSize="+_pageSize;
                        //System.out.println(" URL without Offset: " + qry);
                    }
                    /*
                    res = getObject(qry);

                    if(null != res) {
                        node = map.readTree(res);
                        result = node.findPath("result");
                        _pageCookie = node.get("pagedResultsCookie").textValue();
                        int remainingResults = -1;
                        handleQueryResults(objectClass, resultsHandler, result);
                        if (resultsHandler instanceof SearchResultsHandler) {
                            final SearchResult searchResult = new SearchResult(_pageCookie, SearchResult.CountPolicy.EXACT, _pageSize, -1);
                            ((SearchResultsHandler) resultsHandler).handleResult(searchResult);
                        }
                    }

                     */
                    // Finished initily
                   do {
                        if(null != _pageCookie) {
                            qry = userStr + "&_pageSize=" + _pageSize+"&_pagedResultsCookie="+_pageCookie;
                        } else {
                            qry = userStr + "&_pageSize=" + _pageSize;
                        }
                        res = getObject(qry);
                        if(null != res) {
                            node = map.readTree(res);
                            _pageCookie = node.get("pagedResultsCookie").textValue();
                            int totalRes = node.get("totalPagedResults").asInt();
                            System.out.println("Cookie:" + _pageCookie + " Total Count:" + totalRes);
                            result = node.findPath("result");
                            handleQueryResults(objectClass, resultsHandler, result);
                        }
                   } while(null !=_pageCookie);

                   if (resultsHandler instanceof SearchResultsHandler) {
                       if(null != _pageCookie) {
                           final SearchResult searchResult = new SearchResult(_pageCookie, SearchResult.CountPolicy.EXACT, _pageSize, -1);
                           ((SearchResultsHandler) resultsHandler).handleResult(searchResult);
                       } else {
                           ((SearchResultsHandler) resultsHandler).handleResult(new SearchResult());
                       }
                   }
                   //     }
                        //if(_pagedResultsOffset > 0)
                        //    _pageCookie = null;
                   //

                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        } else if (ObjectClass.GROUP.equals(objectClass)) {
            if (null != uuid) {
                groupStr = idmHost + ":" + idmPort + "/openidm/managed/role/"+uuid.getUidValue();
            } else {
                groupStr = idmHost + ":" + idmPort + "/openidm/managed/role?_queryFilter=roletype%20sw%20%22Entitlement%22";
                if (null != _pageSize) {
                    groupStr = groupStr + "&_pageSize=" + _pageSize;
                }
                if (null != _pageCookie) {
                    groupStr = groupStr + "&_pagedResultsCookie=" + _pageCookie;
                }
            }
            //System.out.println(" URL: "+ groupStr);
            HttpGet httpGet = new HttpGet(groupStr);
            httpGet.setHeader("X-OpenIDM-Username", idmUserId);
            httpGet.setHeader("X-OpenIDM-Password", SecurityUtil.decrypt(idmUserPassword));
            httpGet.setHeader("Accept-API-Version", "resource=1.0");
            httpGet.setHeader("Content-Type", "application/json");
            try {
                CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
                int sc = httpResponse.getStatusLine().getStatusCode();
                if (sc == HttpStatus.SC_OK) {
                    HttpEntity httpEntity = httpResponse.getEntity();
                    if (httpEntity != null) {
                        // return it as a String
                        String res = EntityUtils.toString(httpEntity);
                        if(isGet) {
                            //System.out.println("isGet ");
                            //System.out.println(" Result : " + res);
                            JsonNode node = map.readTree(res);
                            ConnectorObject connectorObject = buildGroupObject(node, objectClass);
                            if (!resultHandle(connectorObject, resultsHandler)) {
                                return;
                            }
                        } else {
                            //System.out.println(res);
                            JsonNode node = map.readTree(res);
                            JsonNode result = node.findPath("result");
                            if(result.isArray()){
                                for(final JsonNode objNode : result) {
                                    //System.out.println(objNode.toString());
                                    ConnectorObject connectorObject = buildGroupObject(objNode, objectClass);
                                    if (!resultHandle(connectorObject, resultsHandler)) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }  catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            logger.warn("Query of type {0} is not supported", objectClass.getObjectClassValue());
            throw new UnsupportedOperationException("Query of type"
                + objectClass.getObjectClassValue() + " is not supported");
        }
    }

    @Override
    public void test() {
        System.out.println("Entering Test");
    }

    private String getObject(String userStr) throws IOException {
        String res = null;
        HttpGet httpGet = new HttpGet(userStr);
        httpGet.setHeader("X-OpenIDM-Username", this.idmUserId);
        httpGet.setHeader("X-OpenIDM-Password", SecurityUtil.decrypt(idmUserPassword));
        httpGet.setHeader("Accept-API-Version", "resource=1.0");
        httpGet.setHeader("Content-Type", "application/json");
        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
        int sc = httpResponse.getStatusLine().getStatusCode();
        if (sc == HttpStatus.SC_OK) {
            HttpEntity httpEntity = httpResponse.getEntity();
            if (httpEntity != null) {
                // return it as a String
                res = EntityUtils.toString(httpEntity);
                //System.out.println("Response: "+ res);
            }
        }
        //System.out.println("getObject returning");
        return res;
    }
    private ObjectClassInfo getUserObjectClassInfo(){
        Set<AttributeInfo> attributes = new HashSet<AttributeInfo>();
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(ObjectClass.ACCOUNT_NAME);
        builder.addAttributeInfo(AttributeInfoBuilder.define("userId").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("userName").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("givenName").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("sn").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("emailAddress").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("roles").setMultiValued(true).build());
        return builder.build();
    }

    private ObjectClassInfo getGroupObjectClassInfo(){
        Set<AttributeInfo> attributes = new HashSet<AttributeInfo>();
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(ObjectClass.GROUP_NAME);
        builder.addAttributeInfo(AttributeInfoBuilder.define("roleId").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("roleName").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("roleType").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("roleApp").setMultiValued(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define("roleAttribute").setMultiValued(false).build());
        return builder.build();
    }
    public ConnectorObject buildUserObject(JsonNode result, ObjectClass objectClass) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        String uid = null;
        uid = result.get("_id").textValue();
        String userName = null;
        if(null != result.get("userName").textValue()) {
            userName = result.get("userName").textValue();
        } else {
            userName = "NA" + new Random().nextInt(10000);
        }
        String givenName = result.get("givenName").textValue();
        String sn = result.get("sn").textValue();
        String mail = result.get("mail").textValue();
        builder.setUid(uid);
        builder.setName(userName);
        System.out.println(" Building user object for: "+userName);
        builder.addAttribute(AttributeBuilder.build("userId",uid));
        builder.addAttribute(AttributeBuilder.build("userName",userName));
        builder.addAttribute(AttributeBuilder.build("givenName",givenName));
        builder.addAttribute(AttributeBuilder.build("sn",sn));
        builder.addAttribute(AttributeBuilder.build("emailAddress",mail));
        ArrayList<String> al = new ArrayList<>();
        String memStr = idmHost+":"+idmPort+"/openidm/managed/user/"+ uid +"/roles?_queryFilter=true&_fields=_ref/*,name";
        //System.out.println(" User URL: "+ memStr);
        CloseableHttpClient httpClient1 =  HttpClients.createDefault();
        HttpGet httpGet1 = new HttpGet(memStr);
        httpGet1.setHeader("X-OpenIDM-Username", idmUserId);
        httpGet1.setHeader("X-OpenIDM-Password", SecurityUtil.decrypt(idmUserPassword));
        httpGet1.setHeader("Accept-API-Version", "resource=1.0");
        httpGet1.setHeader("Content-Type", "application/json");
        try {
            CloseableHttpResponse httpResponse1 = httpClient1.execute(httpGet1);
            int sc = httpResponse1.getStatusLine().getStatusCode();
            if (sc == HttpStatus.SC_OK) {
                HttpEntity httpEntity1 = httpResponse1.getEntity();
                if (httpEntity1 != null) {
                    String res1 = EntityUtils.toString(httpEntity1);
                    //System.out.println(res1);
                    ObjectMapper map1 = new ObjectMapper();
                    JsonNode node1 = map1.readTree(res1);
                    JsonNode result1 = node1.findPath("result");
                    if (result1.isArray()) {
                        for (JsonNode objNode : result1) {
                            String grpName = null;
                            grpName = objNode.get("name").textValue();
                            //System.out.println("User "+ userName +" has Group "+grpName);
                            al.add(grpName);
                        }
                    }
                }
            }
        } catch (Exception e){e.printStackTrace();}
        builder.addAttribute(AttributeBuilder.build("roles",al));
        builder.setObjectClass(objectClass);
        return builder.build();
    }

    public ConnectorObject buildGroupObject(JsonNode node, ObjectClass objectClass) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        String roleId = null;
        String roleName = null;
        String appName = null;
        String roleType = null;
        String roleAttribute = null;
        try {
            roleId = node.get("_id").textValue();
            roleName = node.get("name").textValue();
            appName = node.get("appname").textValue();
            roleType = node.get("roletype").textValue();
            roleAttribute = node.get("attribute").textValue();
        } catch(Exception e) {
            System.out.println("Null");
        }
        builder.setUid(roleId);
        builder.setName(roleName);
        builder.addAttribute(AttributeBuilder.build("roleId",roleId));
        builder.addAttribute(AttributeBuilder.build("roleName",roleName));
        builder.addAttribute(AttributeBuilder.build("roleType",roleType));
        builder.addAttribute(AttributeBuilder.build("roleApp",appName));
        builder.addAttribute(AttributeBuilder.build("roleAttribute",roleAttribute));
        builder.setObjectClass(objectClass);
        return builder.build();
    }
    private ArrayList<IDMRole> getUserGroups(String userId) {
        ArrayList<IDMRole> al = new ArrayList<>();
        String memStr = idmHost+":"+idmPort+"/openidm/managed/user/"+ userId +"/roles?_queryFilter=true&_fields=_ref/*,name";

        CloseableHttpClient httpClient2 =  HttpClients.createDefault();

        HttpGet httpGet2 = new HttpGet(memStr);
        httpGet2.setHeader("X-OpenIDM-Username", idmUserId);
        httpGet2.setHeader("X-OpenIDM-Password", SecurityUtil.decrypt(idmUserPassword));
        httpGet2.setHeader("Accept-API-Version", "resource=1.0");
        httpGet2.setHeader("Content-Type", "application/json");
        try {
            CloseableHttpResponse httpResponse2 = httpClient2.execute(httpGet2);
            int sc = httpResponse2.getStatusLine().getStatusCode();
            if (sc == HttpStatus.SC_OK) {
                HttpEntity httpEntity2 = httpResponse2.getEntity();
                if (httpEntity2 != null) {
                    String res = EntityUtils.toString(httpEntity2);
                    //System.out.println("-----------get User Groups---------");
                    //System.out.println(res);
                    //System.out.println("-----------get User Groups---------");
                    ObjectMapper map = new ObjectMapper();
                    JsonNode node = map.readTree(res);
                    JsonNode result = node.findPath("result");
                    if(result.isArray()) {
                        for (JsonNode objNode : result) {
                                JsonNode node1 = objNode.get("_refProperties");
                                IDMRole r = new IDMRole(objNode.get("_id").textValue(),
                                                        objNode.get("_rev").textValue(),
                                                        objNode.get("_refResourceId").textValue(),
                                                        objNode.get("_refResourceRev").textValue(),
                                                        objNode.get("name").textValue(),
                                                        objNode.get("_ref").textValue(),
                                                        node1.get("_id").textValue(),
                                                        node1.get("_rev").textValue());
                                al.add(r);
                        }
                    }
                }
            }
        }  catch (IOException e) {
            throw new RuntimeException(e);
        }
        return al;
    }

    private void handleRoleMemberships(String uid,List<Object> groups) {
        ArrayList<IDMRole> al = new ArrayList<>();
        ArrayList<String> rolesToKeepList = new ArrayList<>();
        ArrayList<IDMRole> rolesToRemoveList = new ArrayList<>();
        System.out.println("**************** Entering handleRoleMemberships ********");

        try {
            if (groups.size() > 0) {
                System.out.println("**************** End State Groups ********");
                String currentRole = null;
                Iterator i = groups.iterator();
                while (i.hasNext()) {
                    currentRole = (String) i.next();
                    System.out.println("**************** >>" + currentRole);
                    rolesToKeepList.add(currentRole);
                }
            } else {
                System.out.println("**************** User has no Groups left. All Groups will be revoked ********");
            }
        } catch (Exception e1) {   System.out.println("handleRoleMemberships : Error getting end state groups");}
        System.out.println("**************** Comparing Groups ********");
        try {
            System.out.println("**************** Get current Groups ********");
            al = getUserGroups(uid);

            if (al.size() > 0) {
                Iterator alIter = al.iterator();
                while (alIter.hasNext()) {
                    IDMRole roleToFind = (IDMRole) alIter.next();
                    String rName = roleToFind.get_roleName();
                    System.out.println(rName + "<<<<<<** checking ************** >>");
                    if (rolesToKeepList.size() > 0) {
                        if (rolesToKeepList.contains(rName)) {
                            System.out.println("<<<<<<****FOUND " + rName + " ************ >>");
                        } else {
                            System.out.println("Adding " + rName + " to remove list");
                            rolesToRemoveList.add(roleToFind);
                        }
                    } else {
                        System.out.println("Adding " + rName + " to remove list");
                        rolesToRemoveList.add(roleToFind);
                    }
                }
            } else {
                System.out.println("User has no Groups");
            }
        } catch (Exception e1) {   System.out.println("handleRoleMemberships : Error comparing end state and current");}

        if(rolesToRemoveList.size() > 0) {
            System.out.println("**************** Remove Groups ********");
            System.out.println(" role removal for "+ rolesToRemoveList.size() +" roles ");
            if (rolesToRemoveList.size() > 1) {
                System.out.println(" ERROR ");
            } else {
                Iterator rolesToRemoveIter = rolesToRemoveList.iterator();
                while (rolesToRemoveIter.hasNext()) {
                    IDMRole role2Remove = (IDMRole) rolesToRemoveIter.next();
                    System.out.println("<<<<<<****Remove " + role2Remove.get_roleName() + " ************ >>");
                }
                if (removeRole(uid, rolesToRemoveList)) {
                    System.out.println(" roles cleaned up successfully ************ >>");
                } else {
                    System.out.println(" roles clean up failed ************ >>");
                }
            }
        }

        System.out.println("**************** Leaving handleRoleMemberships ********");
    }

    private boolean removeRole(String uid, ArrayList<IDMRole> roles2remove){
        String roleDeleteStr = idmHost+":"+idmPort+"/openidm/managed/user/" + uid;
        CloseableHttpClient httpClient3 =  HttpClients.createDefault();
        try {
            HttpPatch httpPatch3 = new HttpPatch(roleDeleteStr);
            httpPatch3.setHeader("X-OpenIDM-Username", idmUserId);
            httpPatch3.setHeader("X-OpenIDM-Password", SecurityUtil.decrypt(idmUserPassword));
            httpPatch3.setHeader("Accept-API-Version", "resource=1.0");
            httpPatch3.setHeader("Content-Type", "application/json");
            if(roles2remove.size() > 0){
                Iterator roles2removeIter = roles2remove.iterator();
                while(roles2removeIter.hasNext()){
                    IDMRole role = (IDMRole) roles2removeIter.next();
                    System.out.println("**************** Removing"+ role.get_roleName() +" ********");
                    String str = "[ { \"operation\":  \"remove\", \"field\": \"/roles\", " +
                            "\"value\": { \"_ref\": \""+role.get_roleRef()+"\"," +
                            " \"_refResourceCollection\":  \"managed/role\", " +
                            " \"_refResourceId\": \""+ role.get_refResourceId()+"\", " +
                            " \"_refProperties\":  { " +
                            "\"_id\": \"" + role.get_refPropId() +"\", " +
                            "\"_rev\": \""+ role.get_refPropRev() +"\" } } }]";
                    System.out.println(" Payload ");
                    System.out.println(str);
                    StringEntity entity3 = new StringEntity(str);
                    //entity.setContentType();
                    httpPatch3.setEntity(entity3);
                    CloseableHttpResponse httpResponse3 = httpClient3.execute(httpPatch3);
                    String msg = EntityUtils.toString(httpResponse3.getEntity());
                    //System.out.println(msg);
                }
            }
            return true;
        } catch (Exception e){ System.out.println("removeRole : Error removing role");}
        return false;
    }
    private void handleQueryResults(ObjectClass objectClass, ResultsHandler handler,
                                    JsonNode result) {
        System.out.println(" handleQueryResults ");
        for (JsonNode objNode : result) {
            ConnectorObject connectorObject = buildUserObject(objNode, objectClass);
            if (!handler.handle(connectorObject)) {
                break;
            }
        }
    }
    private boolean resultHandle(final Object obj, ResultsHandler resultsHandler) {
        if (obj instanceof ConnectorObject) {
            return ((ResultsHandler) resultsHandler).handle((ConnectorObject) obj);
        } else if (obj instanceof SearchResult && resultsHandler instanceof SearchResultsHandler) {
            ((SearchResultsHandler) resultsHandler).handleResult((SearchResult) obj);
            return true;
        } else if (obj instanceof SyncDelta) {
            return ((SyncResultsHandler) resultsHandler).handle((SyncDelta) obj);
        } else {
            return false;
        }
    }
    private static final Log logger = Log.getLog(RCLConnector.class);

    private Schema _schema;
    private RCLConfiguration _configuration = null;

    private CloseableHttpClient httpClient = null;
    private ConcurrentMap<Uid, Set<Attribute>> _map = null;
    private String idmHost = null;
    private String idmPort = null;
    private String idmUserId = null;
    private GuardedString idmUserPassword = null;

    private String _pagedResultsOffset = null;
    private String _pageCookie = null;
    private Integer _pageSize = null;

    private String userStr = null;

    private String groupStr = null;

    private String idmUserFilter = null;
}