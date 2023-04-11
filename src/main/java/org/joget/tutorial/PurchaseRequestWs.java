package org.joget.tutorial;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.tutorial.model.Items;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PurchaseRequestWs extends DefaultApplicationPlugin {

	@Override
	public String getClassName() {
		return this.getClass().getName();
	}

	@Override
	public String getLabel() {
		return "Training - Purchase Request WS";
	}

	@Override
	public String getPropertyOptions() {
		return AppUtil.readPluginResource(getClassName(), "properties/StoreDataWs.json", null, true, null);
	}

	@Override
	public String getDescription() {
		return "Digunakan untuk memanggil web service - purchase request item";
	}

	@Override
	public String getName() {
		return "Training - Purchase Request WS";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public Object execute(Map arg0) {
		// dapatkan WS URL dari properti
		String url = getPropertyString("url");
		// dapatkan nilai parent ID dari properti
		String parentId = getPropertyString("parentId");
		LogUtil.info(this.getClassName(), "url : " + url);
		LogUtil.info(this.getClassName(), "parentId : " + parentId);
		if (parentId != null && !"".equals(parentId)) {
			// dapatkan data purchase Item dari database berdasarkan parent ID
			List<Items> listItems = getPurchaseItems(parentId);
			// Loop data purchase item, panggil Web Service
			for (Items item : listItems) {
				storeData(item, url);
			}
		}
		return null;
	}

	public List<Items> getPurchaseItems(String parentId) {
		List<Items> list = new ArrayList<>();
		Connection con = null;
		try {
			DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
			con = ds.getConnection();
			if (!con.isClosed()) {
				String strItems = "";
				PreparedStatement ps = con.prepareStatement("SELECT * FROM app_fd_purchase_req WHERE id = ?");
				ps.setObject(1, parentId);
				ResultSet rs = ps.executeQuery();
				// looping hasil querynya
				while (rs.next()) {
					strItems = rs.getString("c_items");
				}
				LogUtil.info(getClassName(), "strItems: " + strItems);
				Items item = null;
				JSONArray jsonArray = new JSONArray(strItems);
				String uuid = "";
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject object = jsonArray.getJSONObject(i);
					item = new Items();
					item.setId(UuidGenerator.getInstance().getUuid());
					item.setName(object.getString("item"));
					item.setQuantity(object.getString("quantity"));
					item.setPrice(object.getString("price"));
					list.add(item);
				}
			}
		} catch (Exception e) {
			LogUtil.error(this.getClassName(), e, e.getMessage());
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (SQLException ex) {
					LogUtil.error(this.getClassName(), ex, ex.getMessage());
				}
			}
		}
		return list;
	}


	
	private boolean storeData(Items item, String WsUrl) {
		boolean result = false;
		// siapkan parameter yang dibutuhkan
		JSONObject param = new JSONObject();
		try {
			param.put("requestId", item.getId());
			param.put("name", item.getName());
			param.put("qty", item.getQuantity());
			param.put("price", item.getPrice());
		} catch (JSONException ex) {
			LogUtil.error(this.getClassName(), ex, ex.getMessage());
		}
		HttpClient client = HttpClientBuilder.create().build();
		HttpPost post = new HttpPost(WsUrl);
		StringEntity input;
		try {
			input = new StringEntity(param.toString());
			input.setContentType("application/json");
			post.setEntity(input);
			HttpResponse respone = client.execute(post);
			// baca hasil WS Call
			BufferedReader rd = new BufferedReader(new InputStreamReader(respone.getEntity().getContent()));
			String line, resultInquiry = "";
			while ((line = rd.readLine()) != null) {
				resultInquiry += line;
			}
			JSONObject resultJson = new JSONObject(resultInquiry);
			String responseCode = resultJson.getString("response_code");
			String responseMessage = resultJson.getString("response_message");
			if(responseCode.equalsIgnoreCase("00")) {
				result = true;
			}
			LogUtil.info(this.getClassName(), "Response Code : " +responseCode);
			LogUtil.info(this.getClassName(), "Response Message : " + responseMessage);
			// cetak hasil WS Call
			LogUtil.info(this.getClassName(), "Result Inquiry : " + resultInquiry);
			
		} catch (UnsupportedEncodingException ex) {
			LogUtil.error(this.getClassName(), ex, ex.getMessage());
		} catch (IOException ex) {
			LogUtil.error(this.getClassName(), ex, ex.getMessage());
		} finally {
			if (client != null) {
				client.getConnectionManager().shutdown();
			}
		}
		return result;
	}


}
