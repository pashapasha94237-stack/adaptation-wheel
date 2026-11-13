package ru.pasha.adaptationwheel.core;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import ru.pasha.adaptationwheel.analysis.NeuralTransport;

/** Loopback fixture only; does not contact any external neural provider or spend tokens. */
public final class NeuralTransportTest {
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newCachedThreadPool(r->{var t=new Thread(r);t.setDaemon(true);return t;}));
        server.createContext("/valid",exchange->{
            var request=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            check("Bearer fixture-only-secret".equals(exchange.getRequestHeaders().getFirst("Authorization")),"Credential header missing");check(request.get("model").getAsString().equals("fixture-model"),"Model missing");check(request.getAsJsonObject("response_format").get("type").getAsString().equals("json_schema"),"Schema missing");
            byte[] bytes=completion("{\"profile\":\"LIGHT\",\"firstSeconds\":12,\"stepSeconds\":25,\"confidence\":80}").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.createContext("/rate",exchange->{exchange.sendResponseHeaders(429,-1);exchange.close();});
        server.createContext("/huge",exchange->{byte[] bytes="x".repeat(20000).getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/bad",exchange->{byte[] bytes=completion("{\"profile\":\"LIGHT\",\"firstSeconds\":-9,\"stepSeconds\":25,\"confidence\":80}").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        server.createContext("/slow",exchange->{try{Thread.sleep(3000);exchange.sendResponseHeaders(200,-1);}catch(Exception ignored){}finally{exchange.close();}});
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","http://127.0.0.1:"+server.getAddress().getPort()+"/valid");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.start();try{
            String base="http://127.0.0.1:"+server.getAddress().getPort();var transport=new NeuralTransport();var facts=new JsonObject();facts.addProperty("damageType","fixture:pulse");
            var advice=transport.request(base+"/valid","fixture-model","fixture-only-secret",facts,true,2);check(advice.profile()==AdaptationProfile.LIGHT&&advice.timings().first()==240,"Actual HTTP response not used");
            for(String path:new String[]{"/rate","/huge","/bad","/slow","/redirect"}){long began=System.nanoTime();boolean rejected=false;try{transport.request(base+path,"fixture-model","",facts,false,1);}catch(Exception expected){rejected=true;}check(rejected,"Failure accepted: "+path);check(System.nanoTime()-began<2_500_000_000L,"Network fallback blocks too long");}
            for(String endpoint:new String[]{"http://example.com/v1/chat/completions","https://key:secret@example.com/v1/chat/completions","https://example.com/path?key=secret"}){boolean rejected=false;try{NeuralTransport.endpoint(endpoint);}catch(RuntimeException expected){rejected=true;}check(rejected,"Unsafe endpoint accepted");}
            System.out.println("NeuralTransportTest: real loopback HTTP, request/schema/auth, valid advice, timeout, HTTP429, malformed/oversize response, redirect and endpoint limits passed. No external requests.");
        }finally{server.stop(0);}
    }
    private static String completion(String content){JsonObject message=new JsonObject();message.addProperty("content",content);JsonObject choice=new JsonObject();choice.addProperty("finish_reason","stop");choice.add("message",message);JsonArray choices=new JsonArray();choices.add(choice);JsonObject result=new JsonObject();result.add("choices",choices);return result.toString();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
