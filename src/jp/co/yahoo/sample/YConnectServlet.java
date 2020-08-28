package jp.co.yahoo.sample;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.co.yahoo.yconnect.YConnectExplicit;
import jp.co.yahoo.yconnect.core.api.ApiClientException;
import jp.co.yahoo.yconnect.core.oauth2.AuthorizationException;
import jp.co.yahoo.yconnect.core.oauth2.OAuth2ResponseType;
import jp.co.yahoo.yconnect.core.oauth2.TokenException;
import jp.co.yahoo.yconnect.core.oidc.IdTokenObject;
import jp.co.yahoo.yconnect.core.oidc.OIDCDisplay;
import jp.co.yahoo.yconnect.core.oidc.OIDCPrompt;
import jp.co.yahoo.yconnect.core.oidc.OIDCScope;
import jp.co.yahoo.yconnect.core.oidc.UserInfoObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Servlet implementation class YConnectServlet
 *
 * @author Copyright (C) 2012 Yahoo Japan Corporation. All Rights Reserved.
 *
 */
public class YConnectServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	// アプリケーションID, シークレット
	private String clientId;
	//private final static String clientId = "dj0zaiZpPVJoTEFYYzkxYWV5aCZzPWNvbnN1bWVyc2VjcmV0Jng9NWY-";
	private String clientSecret;

	// コールバックURL
	// (アプリケーションID発行時に登録したURL)
	private static final String redirectUri = "http://localhost:8000/YConnectServlet/YConnectServlet";
	
	private Connection con;

	public YConnectServlet() throws Exception {
		super();
		con = JdbcConnection.getConnection();
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		response.setContentType("text/html; charset=UTF-8");

		// state、nonceにランダムな値を初期化
		String state = "5Ye65oi744KKT0vjgafjgZnjgojjgIHlhYjovKnjg4M="; // リクエストとコールバック間の検証用のランダムな文字列を指定してください
		String nonce = "SUTljqjjga8uLi7jgrrjg4Plj4vjgaDjgoc="; // リプレイアタック対策のランダムな文字列を指定してください

		// YConnectインスタンス生成
		YConnectExplicit yconnect = new YConnectExplicit();

		// SSL証明書チェック無効
		// (※Production環境では必ず有効にすること)
		// YConnectExplicit.disableSSLCheck();

		try {

			// コールバックURLから各パラメーターを抽出
			if (yconnect.hasAuthorizationCode(request.getQueryString())) {

				/*********************************************************
				 * Parse the Callback URI and Save the Access Token.
				 *********************************************************/

				StringBuffer sb = new StringBuffer();

				// 認可コードを取得
				//String code = yconnect.getAuthorizationCode(state);
				String code = request.getParameter("code");

				sb.append("<h1>Authorization Request</h1>");
				sb.append("Authorization Code: " + code + "<br/><br/>");

				/***********************************************
				 * Request Access Token adn Refresh Token.
				 ***********************************************/

				// Tokenエンドポイントにリクエスト
				yconnect.requestToken(code, clientId, clientSecret, redirectUri);
				// アクセストークン、リフレッシュトークン、IDトークンを取得
				String accessTokenString = yconnect.getAccessToken();
				long expiration = yconnect.getAccessTokenExpiration();
				String refreshToken = yconnect.getRefreshToken();
				String idTokenString = yconnect.getIdToken();

				sb.append("<h1>Access Token Request</h1>");
				sb.append("Access Token: " + accessTokenString + "<br/><br/>");
				sb.append("Expiration: " + Long.toString(expiration) + "<br/><br/>");
				sb.append("Refresh Token: " + refreshToken + "<br/><br/>");

				/************************
				 * Decode ID Token.
				 ************************/

				// IDトークンを復号化、値の検証
				//IdTokenObject idTokenObject = yconnect.decodeIdToken(idTokenString, nonce, clientId, clientSecret);
				IdTokenObject idTokenObject = yconnect.decodeIdToken(idTokenString);
				
				sb.append("<h1>ID Token</h1>");
				sb.append("ID Token: " + idTokenObject.toString() + "<br/><br/>");

				/*************************
				 * Request UserInfo.
				 *************************/

				// UserInfoエンドポイントへリクエスト
				yconnect.requestUserInfo(accessTokenString);
				// UserInfo情報を取得
				UserInfoObject userInfoObject = yconnect.getUserInfoObject();
				sb.append("<h1>UserInfo Request</h1>");
				sb.append("UserInfo: <pre>" + userInfoObject + "</pre><br/>");
				
				String sql = "UPDATE rakuten.shop SET ACCESS_TOKEN=?,REFRESH_TOKEN=?,LOGIN_TIME=? WHERE SITE='Yahoo' and SHOP_ID=?";
				PreparedStatement ps = con.prepareStatement(sql);
				ps.setString(1, accessTokenString);
				ps.setString(2, refreshToken);
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
				Calendar date = Calendar.getInstance();
				String updateTime = sdf.format(date.getTime());
				ps.setString(3, updateTime);
				ps.setString(4, clientSecret);
				ps.execute();
				con.close();

				PrintWriter out = response.getWriter();
				out.println(new String(sb));
				out.close();

			} else {
				
				String shop = request.getParameter("shop");
				String sql = "SELECT YAHOO_APP_ID FROM rakuten.shop WHERE SITE = 'Yahoo' AND SHOP_ID = ?";
				PreparedStatement ps = con.prepareStatement(sql);
				ps.setString(1, shop);
				
				ResultSet rs = ps.executeQuery();
				while (rs.next()) {
					clientId = rs.getString("YAHOO_APP_ID");
				}
				
				clientSecret = shop;

				/****************************************************************
				 * Request Authorization Endpoint for getting Access Token.
				 ****************************************************************/

				// 各パラメーター初期化
				String responseType = OAuth2ResponseType.CODE_IDTOKEN;
				String display = OIDCDisplay.DEFAULT;
				String[] prompt = { OIDCPrompt.DEFAULT };
				String[] scope = { OIDCScope.OPENID, OIDCScope.PROFILE,
						OIDCScope.EMAIL, OIDCScope.ADDRESS };

				// 各パラメーターを設定
				yconnect.init(clientId, redirectUri, state, responseType, display, prompt, scope, nonce);
				URI uri = yconnect.generateAuthorizationUri();

				// Authorizationエンドポイントにリダイレクト(同意画面を表示)
				response.sendRedirect(uri.toString());
			}

		} catch (ApiClientException ace) {

			// アクセストークンが有効期限切れであるかチェック
			if (ace.isInvalidToken()) {

				/*****************************
				 * Refresh Access Token.
				 *****************************/

				try {

					// 保存していたリフレッシュトークンを指定してください
					String refreshToken = "STORED_REFRESH_TOKEN";

					// Tokenエンドポイントにリクエストしてアクセストークンを更新
					yconnect.refreshToken(refreshToken, clientId, clientSecret);
					String accessTokenString = yconnect.getAccessToken();
					long expiration = yconnect.getAccessTokenExpiration();

					StringBuffer sb = new StringBuffer();
					sb.append("<h1>Refresh Token</h1>");
					sb.append("Access Token: " + accessTokenString + "<br/><br/>");
					sb.append("Expiration: " + Long.toString(expiration) + "<br/><br/>");

					PrintWriter out = response.getWriter();
					out.println(new String(sb));
					out.close();

				} catch (TokenException te) {

					// リフレッシュトークンの有効期限切れチェック
					if (te.isInvalidGrant()) {
						// はじめのAuthorizationエンドポイントリクエストからやり直してください
					}

					te.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
			ace.printStackTrace();
		} catch (AuthorizationException e) {
			e.printStackTrace();
		} catch (TokenException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
