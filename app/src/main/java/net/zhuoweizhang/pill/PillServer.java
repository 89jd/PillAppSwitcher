package net.zhuoweizhang.pill;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/*
CLASSPATH="$(pm path net.zhuoweizhang.pill|sed -e s/^package//)" app_process /sdcard net.zhuoweizhang.pill.PillServer
 */
public final class PillServer extends NanoHTTPD {
  public PillServer() {
    super("localhost", 11133);
  }

  @Override
  public Response serve(IHTTPSession session) {
    try {
      if (session.getUri().equals("/")) {
        return newFixedLengthResponse(Response.Status.OK, "application/json", genJson().toString());
      }
      if (session.getUri().startsWith("/thumb/")) {
        int id = Integer.parseInt(session.getUri().substring("/thumb/".length()));
        byte[] bytes = getTaskThumbnail(id);
        if (bytes == null) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found");
        return newFixedLengthResponse(Response.Status.OK, "image/jpg", new ByteArrayInputStream(bytes), bytes.length);
      }
      return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found");
    } catch (Exception e) {
      e.printStackTrace();
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.toString());
    }
  }

  private JSONArray genJson() throws Exception {
    List<ActivityManager.RecentTaskInfo> tasks = getRecentTasks(5, 0, 0);
    JSONArray jsonArray = new JSONArray();
    for (ActivityManager.RecentTaskInfo taskInfo: tasks) {
      JSONObject jsonObject = new JSONObject();
      System.out.println("serving: " + taskInfo.toString());
      jsonObject.put("id", taskInfo.id);
      jsonObject.put("persistentId", taskInfo.persistentId);
      jsonObject.put("topPackage", taskInfo.topActivity == null? null: taskInfo.topActivity.getPackageName());
      jsonArray.put(jsonObject);
    }
    return jsonArray;
  }

  private byte[] getTaskThumbnail(int id) throws Exception {
    long start = System.currentTimeMillis();
    Object iam = getIAM();
    Object thumbnail = iam.getClass().getMethod("getTaskSnapshot", Integer.TYPE, Boolean.TYPE).invoke(iam, id, false);
    if (thumbnail == null) return null;
    Object graphicBuffer = (Object)thumbnail.getClass().getMethod("getSnapshot").invoke(thumbnail);
    if (graphicBuffer == null) return null;
    System.out.println(System.currentTimeMillis() - start);
    Bitmap bmp = (Bitmap)Bitmap.class.getMethod("createHardwareBitmap", graphicBuffer.getClass()).invoke(null, graphicBuffer);
    if (bmp == null) return null;
    System.out.println("create " + (System.currentTimeMillis() - start));
    Bitmap scaledBmp = Bitmap.createScaledBitmap(bmp, bmp.getWidth(), bmp.getHeight(), false);
    System.out.println(System.currentTimeMillis() - start);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    scaledBmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
    System.out.println(System.currentTimeMillis() - start);
    return baos.toByteArray();
  }

  public List<ActivityManager.RecentTaskInfo> getRecentTasks(int maxNum, int flags, int userId) throws Exception {
    Object iam = getIAM();
    Object tasksParcelled = iam.getClass().getMethod("getRecentTasks", Integer.TYPE,
      Integer.TYPE, Integer.TYPE).invoke(iam, 25, 0, 0);
    List<ActivityManager.RecentTaskInfo> tasks = (List<ActivityManager.RecentTaskInfo>)tasksParcelled.getClass().getMethod("getList").invoke(tasksParcelled);
    return tasks;
  }

  private static Object getIAM() throws Exception {
    Object iam = ActivityManager.class.getMethod("getService").invoke(null);
    return iam;
  }
  public static void main(String[] args) throws Exception {
    PillServer server = new PillServer();
    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    System.in.read();
  }
}
