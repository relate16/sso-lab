package com.ssolab.gallery.artwork;

import java.math.BigDecimal;

public record ArtworkSearchCriteria(
    String query,
    SaleStatus saleStatus,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    SizeBand sizeBand,
    Order order
) {
    public enum SizeBand { SMALL, MEDIUM, LARGE }
    public enum Order { RECENT, PRICE_ASC, PRICE_DESC }
}
