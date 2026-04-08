package ru.maltsev.primemarketbackend.market.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.maltsev.primemarketbackend.market.service.MarketIntent;
import ru.maltsev.primemarketbackend.market.service.MarketPriceSort;

@Repository
@RequiredArgsConstructor
public class MarketOfferQueryRepository {
    private static final String ACTIVE_OFFERS_FROM_WHERE = """
        from offers o
        join games g
          on g.id = o.game_id
         and g.is_active = true
        join categories c
          on c.id = o.category_id
         and c.is_active = true
        join users u
          on u.id = o.user_id
         and u.is_active = true
        left join currency_rates cr
          on %s
        where o.status = 'active'
          and o.published_at is not null
          and o.game_id = :gameId
          and o.category_id = :categoryId
          and o.side = :offerSide
          and o.price_amount is not null
          and o.price_currency_code is not null
          and (o.price_currency_code = :viewerCurrencyCode or cr.id is not null)
        """;
    private static final String BUY_RATE_JOIN = """
        o.price_currency_code <> :viewerCurrencyCode
        and cr.from_currency_code = :viewerCurrencyCode
        and cr.to_currency_code = o.price_currency_code
        """;
    private static final String SELL_RATE_JOIN = """
        o.price_currency_code <> :viewerCurrencyCode
        and cr.from_currency_code = o.price_currency_code
        and cr.to_currency_code = :viewerCurrencyCode
        """;
    private static final String RATE_EXPRESSION = """
        case
            when o.price_currency_code = :viewerCurrencyCode then cast(1 as numeric(20, 8))
            else cr.rate
        end
        """;
    private static final String BUY_DISPLAY_PRICE_EXPRESSION = """
        case
            when o.price_currency_code = :viewerCurrencyCode then o.price_amount
            else round(o.price_amount / cr.rate, 8)
        end
        """;
    private static final String SELL_DISPLAY_PRICE_EXPRESSION = """
        case
            when o.price_currency_code = :viewerCurrencyCode then o.price_amount
            else round(o.price_amount * cr.rate, 8)
        end
        """;
    private static final String MARKET_DETAILS_FROM_WHERE = """
        from offers o
        join games g
          on g.id = o.game_id
         and g.is_active = true
        join categories c
          on c.id = o.category_id
         and c.is_active = true
         and lower(c.slug) = 'currency'
        join users u
          on u.id = o.user_id
         and u.is_active = true
        left join currency_rates cr
          on %s
        where o.id = :offerId
          and o.status = 'active'
          and o.published_at is not null
          and o.side = :offerSide
          and o.price_amount is not null
          and o.price_currency_code is not null
          and (o.price_currency_code = :viewerCurrencyCode or cr.id is not null)
        """;
    private static final RowMapper<MarketOfferRow> MARKET_OFFER_ROW_MAPPER = (rs, rowNum) -> new MarketOfferRow(
        rs.getLong("id"),
        rs.getString("side"),
        rs.getLong("game_id"),
        rs.getString("game_slug"),
        rs.getString("game_title"),
        rs.getLong("category_id"),
        rs.getString("category_slug"),
        rs.getString("category_title"),
        rs.getString("owner_username"),
        rs.getString("title"),
        rs.getString("description"),
        rs.getString("trade_terms"),
        rs.getBigDecimal("display_price_amount"),
        rs.getBigDecimal("rate"),
        rs.getBigDecimal("quantity"),
        rs.getBigDecimal("min_trade_quantity"),
        rs.getBigDecimal("max_trade_quantity"),
        rs.getBigDecimal("quantity_step"),
        getInstant(rs, "published_at")
    );
    private static final RowMapper<MarketOfferContextRecord> CONTEXT_ROW_MAPPER = (rs, rowNum) -> new MarketOfferContextRecord(
        rs.getLong("offer_id"),
        rs.getString("dimension_slug"),
        rs.getString("value_slug"),
        rs.getString("value_title")
    );
    private static final RowMapper<MarketOfferAttributeRecord> ATTRIBUTE_ROW_MAPPER = (rs, rowNum) -> new MarketOfferAttributeRecord(
        rs.getLong("offer_id"),
        rs.getString("attribute_slug"),
        rs.getString("option_slug"),
        rs.getString("option_title")
    );
    private static final RowMapper<MarketOfferDeliveryMethodRecord> DELIVERY_METHOD_ROW_MAPPER = (rs, rowNum) -> new MarketOfferDeliveryMethodRecord(
        rs.getLong("offer_id"),
        rs.getString("slug"),
        rs.getString("title")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MarketOfferPageData findCurrencyOffers(MarketOfferSearchCriteria criteria) {
        SqlFragments sqlFragments = buildSqlFragments(criteria);
        long total = queryTotal(sqlFragments);
        if (total == 0) {
            return new MarketOfferPageData(List.of(), 0L);
        }

        List<MarketOfferRow> rows = queryRows(criteria, sqlFragments);
        if (rows.isEmpty()) {
            return new MarketOfferPageData(List.of(), total);
        }

        return new MarketOfferPageData(toOfferRecords(rows, criteria.viewerCurrencyCode()), total);
    }

    public Optional<MarketOfferRecord> findCurrencyOfferById(
        Long offerId,
        MarketIntent intent,
        String viewerCurrencyCode
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("offerId", offerId)
            .addValue("offerSide", intent.offerSide())
            .addValue("viewerCurrencyCode", viewerCurrencyCode);
        String fromWhere = MARKET_DETAILS_FROM_WHERE.formatted(rateJoin(intent));
        String sql = buildSelectSql(
            intent == MarketIntent.BUY ? BUY_DISPLAY_PRICE_EXPRESSION : SELL_DISPLAY_PRICE_EXPRESSION,
            fromWhere
        );
        List<MarketOfferRow> rows = jdbcTemplate.query(sql, parameters, MARKET_OFFER_ROW_MAPPER);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        return toOfferRecords(rows, viewerCurrencyCode).stream().findFirst();
    }

    private long queryTotal(SqlFragments sqlFragments) {
        String countSql = "select count(*) " + sqlFragments.fromWhereClause();
        Long total = jdbcTemplate.queryForObject(countSql, sqlFragments.parameters(), Long.class);
        return total == null ? 0L : total;
    }

    private List<MarketOfferRow> queryRows(MarketOfferSearchCriteria criteria, SqlFragments sqlFragments) {
        String selectSql = buildSelectSql(
            sqlFragments.displayPriceExpression(),
            sqlFragments.fromWhereClause()
        ) + """
            order by %s
            limit :limit
            offset :offset
            """.formatted(toOrderBy(criteria.sort()));

        MapSqlParameterSource parameters = copyOf(sqlFragments.parameters())
            .addValue("limit", criteria.size())
            .addValue("offset", (long) criteria.page() * criteria.size());
        return jdbcTemplate.query(selectSql, parameters, MARKET_OFFER_ROW_MAPPER);
    }

    private String buildSelectSql(String displayPriceExpression, String fromWhereClause) {
        return """
            select
                o.id,
                o.side,
                g.id as game_id,
                g.slug as game_slug,
                g.title as game_title,
                c.id as category_id,
                c.slug as category_slug,
                c.title as category_title,
                u.username as owner_username,
                o.title,
                o.description,
                o.trade_terms,
                %s as display_price_amount,
                %s as rate,
                o.quantity,
                o.min_trade_quantity,
                o.max_trade_quantity,
                o.quantity_step,
                o.published_at
            %s
            """.formatted(
            displayPriceExpression,
            RATE_EXPRESSION,
            fromWhereClause
        );
    }

    private Map<Long, List<MarketOfferContextRecord>> loadContexts(List<Long> offerIds) {
        String sql = """
            select
                ocv.offer_id,
                cd.slug as dimension_slug,
                cdv.slug as value_slug,
                cdv.title as value_title
            from offer_context_values ocv
            join context_dimensions cd
              on cd.id = ocv.context_dimension_id
            join context_dimension_values cdv
              on cdv.id = ocv.context_dimension_value_id
            where ocv.offer_id in (:offerIds)
            order by ocv.offer_id asc, cd.sort_order asc, cd.title asc
            """;
        List<MarketOfferContextRecord> rows = jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("offerIds", offerIds),
            CONTEXT_ROW_MAPPER
        );
        return groupByOfferId(rows, MarketOfferContextRecord::offerId);
    }

    private Map<Long, List<MarketOfferAttributeRecord>> loadAttributes(List<Long> offerIds) {
        String sql = """
            select
                oav.offer_id,
                ca.slug as attribute_slug,
                cao.slug as option_slug,
                cao.title as option_title
            from offer_attribute_values oav
            join category_attributes ca
              on ca.id = oav.category_attribute_id
            left join category_attribute_options cao
              on cao.id = oav.category_attribute_option_id
            where oav.offer_id in (:offerIds)
            order by oav.offer_id asc, ca.sort_order asc, ca.title asc
            """;
        List<MarketOfferAttributeRecord> rows = jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("offerIds", offerIds),
            ATTRIBUTE_ROW_MAPPER
        );
        return groupByOfferId(rows, MarketOfferAttributeRecord::offerId);
    }

    private Map<Long, List<MarketOfferDeliveryMethodRecord>> loadDeliveryMethods(List<Long> offerIds) {
        String sql = """
            select
                odm.offer_id,
                dm.slug,
                dm.title
            from offer_delivery_methods odm
            join delivery_methods dm
              on dm.id = odm.delivery_method_id
            where odm.offer_id in (:offerIds)
            order by odm.offer_id asc, dm.sort_order asc, dm.title asc
            """;
        List<MarketOfferDeliveryMethodRecord> rows = jdbcTemplate.query(
            sql,
            new MapSqlParameterSource("offerIds", offerIds),
            DELIVERY_METHOD_ROW_MAPPER
        );
        return groupByOfferId(rows, MarketOfferDeliveryMethodRecord::offerId);
    }

    private SqlFragments buildSqlFragments(MarketOfferSearchCriteria criteria) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("gameId", criteria.gameId())
            .addValue("categoryId", criteria.categoryId())
            .addValue("offerSide", criteria.offerSide())
            .addValue("viewerCurrencyCode", criteria.viewerCurrencyCode());

        StringBuilder fromWhere = new StringBuilder(ACTIVE_OFFERS_FROM_WHERE.formatted(rateJoin(criteria.intent())));
        appendContextFilter(fromWhere, parameters, "platform", criteria.platform());
        appendContextFilter(fromWhere, parameters, "league", criteria.league());
        appendContextFilter(fromWhere, parameters, "mode", criteria.mode());
        appendContextFilter(fromWhere, parameters, "ruthless", criteria.ruthless());
        appendAttributeFilter(fromWhere, parameters, criteria.currencyType());

        return new SqlFragments(
            fromWhere.toString(),
            criteria.intent() == MarketIntent.BUY ? BUY_DISPLAY_PRICE_EXPRESSION : SELL_DISPLAY_PRICE_EXPRESSION,
            parameters
        );
    }

    private List<MarketOfferRecord> toOfferRecords(List<MarketOfferRow> rows, String viewerCurrencyCode) {
        List<Long> offerIds = rows.stream()
            .map(MarketOfferRow::id)
            .toList();

        Map<Long, List<MarketOfferContextRecord>> contextsByOfferId = loadContexts(offerIds);
        Map<Long, List<MarketOfferAttributeRecord>> attributesByOfferId = loadAttributes(offerIds);
        Map<Long, List<MarketOfferDeliveryMethodRecord>> deliveryMethodsByOfferId = loadDeliveryMethods(offerIds);

        return rows.stream()
            .map(row -> new MarketOfferRecord(
                row.id(),
                row.side(),
                row.gameId(),
                row.gameSlug(),
                row.gameTitle(),
                row.categoryId(),
                row.categorySlug(),
                row.categoryTitle(),
                row.ownerUsername(),
                row.title(),
                row.description(),
                row.tradeTerms(),
                row.displayPriceAmount(),
                viewerCurrencyCode,
                row.rate(),
                row.quantity(),
                row.minTradeQuantity(),
                row.maxTradeQuantity(),
                row.quantityStep(),
                contextsByOfferId.getOrDefault(row.id(), List.of()),
                attributesByOfferId.getOrDefault(row.id(), List.of()),
                deliveryMethodsByOfferId.getOrDefault(row.id(), List.of()),
                row.publishedAt()
            ))
            .toList();
    }

    private void appendContextFilter(
        StringBuilder fromWhere,
        MapSqlParameterSource parameters,
        String dimensionSlug,
        String valueSlug
    ) {
        if (valueSlug == null) {
            return;
        }

        String dimensionParameterName = dimensionSlug + "DimensionSlug";
        String valueParameterName = dimensionSlug + "ValueSlug";
        fromWhere.append("""

              and exists (
                  select 1
                  from offer_context_values ocv_filter
                  join context_dimensions cd_filter
                    on cd_filter.id = ocv_filter.context_dimension_id
                  join context_dimension_values cdv_filter
                    on cdv_filter.id = ocv_filter.context_dimension_value_id
                  where ocv_filter.offer_id = o.id
                    and cd_filter.slug = :%s
                    and cdv_filter.slug = :%s
              )
            """.formatted(dimensionParameterName, valueParameterName));
        parameters
            .addValue(dimensionParameterName, dimensionSlug)
            .addValue(valueParameterName, valueSlug);
    }

    private void appendAttributeFilter(
        StringBuilder fromWhere,
        MapSqlParameterSource parameters,
        String currencyType
    ) {
        if (currencyType == null) {
            return;
        }

        fromWhere.append("""

              and exists (
                  select 1
                  from offer_attribute_values oav_filter
                  join category_attributes ca_filter
                    on ca_filter.id = oav_filter.category_attribute_id
                  join category_attribute_options cao_filter
                    on cao_filter.id = oav_filter.category_attribute_option_id
                  where oav_filter.offer_id = o.id
                    and ca_filter.slug = 'currency-type'
                    and cao_filter.slug = :currencyType
              )
            """);
        parameters.addValue("currencyType", currencyType);
    }

    private String rateJoin(MarketIntent intent) {
        return intent == MarketIntent.BUY ? BUY_RATE_JOIN : SELL_RATE_JOIN;
    }

    private String toOrderBy(MarketPriceSort sort) {
        return switch (sort) {
            case PRICE_ASC -> "display_price_amount asc, o.published_at desc, o.id asc";
            case PRICE_DESC -> "display_price_amount desc, o.published_at desc, o.id desc";
        };
    }

    private static MapSqlParameterSource copyOf(MapSqlParameterSource source) {
        MapSqlParameterSource copy = new MapSqlParameterSource();
        for (String parameterName : source.getParameterNames()) {
            copy.addValue(parameterName, source.getValue(parameterName));
        }
        return copy;
    }

    private static Instant getInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static <T> Map<Long, List<T>> groupByOfferId(List<T> rows, OfferIdExtractor<T> offerIdExtractor) {
        Map<Long, List<T>> grouped = new LinkedHashMap<>();
        for (T row : rows) {
            grouped.computeIfAbsent(offerIdExtractor.offerId(row), key -> new ArrayList<>())
                .add(row);
        }
        return grouped;
    }

    @FunctionalInterface
    private interface OfferIdExtractor<T> {
        Long offerId(T value);
    }

    private record SqlFragments(
        String fromWhereClause,
        String displayPriceExpression,
        MapSqlParameterSource parameters
    ) {
    }

    private record MarketOfferRow(
        Long id,
        String side,
        Long gameId,
        String gameSlug,
        String gameTitle,
        Long categoryId,
        String categorySlug,
        String categoryTitle,
        String ownerUsername,
        String title,
        String description,
        String tradeTerms,
        BigDecimal displayPriceAmount,
        BigDecimal rate,
        BigDecimal quantity,
        BigDecimal minTradeQuantity,
        BigDecimal maxTradeQuantity,
        BigDecimal quantityStep,
        Instant publishedAt
    ) {
    }

    public record MarketOfferPageData(
        List<MarketOfferRecord> items,
        long total
    ) {
    }

    public record MarketOfferRecord(
        Long id,
        String side,
        Long gameId,
        String gameSlug,
        String gameTitle,
        Long categoryId,
        String categorySlug,
        String categoryTitle,
        String ownerUsername,
        String title,
        String description,
        String tradeTerms,
        BigDecimal displayPriceAmount,
        String viewerCurrencyCode,
        BigDecimal rate,
        BigDecimal quantity,
        BigDecimal minTradeQuantity,
        BigDecimal maxTradeQuantity,
        BigDecimal quantityStep,
        List<MarketOfferContextRecord> contexts,
        List<MarketOfferAttributeRecord> attributes,
        List<MarketOfferDeliveryMethodRecord> deliveryMethods,
        Instant publishedAt
    ) {
    }

    public record MarketOfferContextRecord(
        Long offerId,
        String dimensionSlug,
        String valueSlug,
        String valueTitle
    ) {
    }

    public record MarketOfferAttributeRecord(
        Long offerId,
        String attributeSlug,
        String optionSlug,
        String optionTitle
    ) {
    }

    public record MarketOfferDeliveryMethodRecord(
        Long offerId,
        String slug,
        String title
    ) {
    }
}
