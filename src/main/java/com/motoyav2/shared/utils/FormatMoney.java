package com.motoyav2.shared.utils;

import java.text.NumberFormat;
import java.util.Locale;

public class FormatMoney {

  public static final NumberFormat PEN_FORMAT =
      NumberFormat.getCurrencyInstance(new Locale("es", "PE"));
}
