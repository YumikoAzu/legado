#### 2020-12-22
找到toolbar上加载MENU设置ICON颜色的方法了。

menu里,需要设置app:iconTint，设置成android:iconTint没用。。。。。。

代码里menu[i].icon.setTint(Color)就生效了

状态栏还要再看看。toolbar上返回键和overFlow图标要再看看，不过这个应该好找。


