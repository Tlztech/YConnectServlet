<%@ page contentType="text/html;charset=UTF-8" language="java" import="java.sql.*" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<!DOCTYPE HTML>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>ヤフー登録</title>
</head>
<body>
<!-- <h1>YConnect Servlet Sample</h1> -->
<!-- <a href="YConnectServlet"> -->
<!-- <img src="http://i.yimg.jp/images/login/btn/btnXSYid.gif" width="241" height="28" alt="Yahoo! JAPAN IDでログイン" border="0"> -->
<!-- </a> -->
<%
Connection conn = null;
List<String> shopList = new ArrayList<String>();
try {
	conn=jp.co.yahoo.sample.JdbcConnection.getConnection();
	String sql = "SELECT SHOP_ID FROM rakuten.shop WHERE SITE = 'Yahoo'";
	PreparedStatement ps = conn.prepareStatement(sql);
	ResultSet rs = ps.executeQuery();
	while (rs.next()) {
		shopList.add(rs.getString("SHOP_ID"));
	}
} catch (Exception e) {
	out.println(e);
} finally {
	conn.close();
}
%>
<form action="YConnectServlet">
<p>
店舗：<input type="text" list="shop_list" name="shop" />
<datalist id="shop_list">
<%for(int i=0;i<shopList.size();i++){%>
<option label=<%=shopList.get(i)%> value=<%=shopList.get(i)%> />
<%}%>
</datalist>
</p>
<input type="image" src="http://i.yimg.jp/images/login/btn/btnXSYid.gif" alt="Yahoo! JAPAN IDでログイン" />
</form>

</body>
</html>