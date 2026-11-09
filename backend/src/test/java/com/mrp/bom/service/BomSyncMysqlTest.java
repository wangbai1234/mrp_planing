package com.mrp.bom.service;

import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.local.repository.BomLocalMapper;
import com.mrp.bom.repository.BomMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a disposable MySQL database; never connects to application master data. */
@Testcontainers
class BomSyncMysqlTest {
    static { System.setProperty("api.version", System.getProperty("api.version", "1.40")); }
    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void higherWuxiVersionDoesNotSuppressShanghaiAndWuxiOnlyParentIsExcluded() throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS V_BOM_OUTPUT");
        String columns = "INVCODE,INVNAME,INVCODE_B,INVNAME_B,HVERSION,MATERIALSPEC1,MAKEFACTORY1,VNOTE," +
                "VREPLACEINDEX,INVCODE_TD,INVNAME_TD,MATERIALSPEC2,MAKEFACTORY2,BDELIVER,HBDEFAULT," +
                "NAME,D3NAME,D4NAME,D5NAME,M2DEF17,ORGNAME,BODYCODE";
        String ddl = String.join(",", java.util.Arrays.stream(columns.split(","))
                .map(name -> name + " VARCHAR(255)").toList());
        jdbc.execute("CREATE TABLE V_BOM_OUTPUT (" + ddl +
                ",HNASSPARENTNUM BIGINT,NASSITEMNUM BIGINT,NASSITEMNUM2 BIGINT,CBEGINPERIOD DATE)");
        String insert = "INSERT INTO V_BOM_OUTPUT (INVCODE,INVCODE_B,HVERSION,ORGNAME,HNASSPARENTNUM,NASSITEMNUM) " +
                "VALUES (?,?,?,?,1,2)";
        jdbc.update(insert, "P", "C", "1.0", BomService.PLANNING_ORGANIZATION);
        jdbc.update(insert, "P", "C", "2.0", BomService.PLANNING_ORGANIZATION);
        jdbc.update(insert, "P", "C", "9.0", "七十迈科技（无锡）有限公司");
        jdbc.update(insert, "WX_ONLY", "C2", "1.0", "七十迈科技（无锡）有限公司");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/bom/BomMapper.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        BomMapper mapper = new SqlSessionTemplate(factory.getObject()).getMapper(BomMapper.class);

        List<BomDetail> rows = mapper.selectAllLatestBom(BomService.PLANNING_ORGANIZATION);

        assertEquals(1, rows.size());
        assertEquals("P", rows.get(0).getParentCode());
        assertEquals("2.0", rows.get(0).getVersion());
        assertEquals(BomService.PLANNING_ORGANIZATION, rows.get(0).getOrgName());
    }

    @Test
    void failureAfterDeleteRollsBackLocalWrites() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sync_probe (code VARCHAR(100) PRIMARY KEY)");
        jdbc.update("DELETE FROM sync_probe");
        jdbc.update("INSERT INTO sync_probe VALUES ('ORIGINAL')");
        BomMapper source = mock(BomMapper.class);
        BomLocalMapper local = mock(BomLocalMapper.class);
        BomDetail row = new BomDetail();
        row.setParentCode("NEW"); row.setChildCode("C");
        row.setOrgName(BomService.PLANNING_ORGANIZATION);
        when(source.countLatestBom(BomService.PLANNING_ORGANIZATION)).thenReturn(1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ResultHandler<BomDetail> handler = invocation.getArgument(1);
            DefaultResultContext<BomDetail> context = new DefaultResultContext<>();
            context.nextResultObject(row);
            handler.handleResult(context);
            return null;
        }).when(source).streamLatestBom(eq(BomService.PLANNING_ORGANIZATION), any());
        doAnswer(invocation -> jdbc.update("DELETE FROM sync_probe")).when(local).deleteAllDetails();
        doAnswer(invocation -> jdbc.update("INSERT INTO sync_probe VALUES ('NEW')")).when(local).insertParentBatch(anyList());
        doThrow(new IllegalStateException("injected write failure")).when(local).insertDetailBatch(anyList());
        ProxyFactory proxy = new ProxyFactory(new BomService(source, local));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        BomService service = (BomService) proxy.getProxy();

        assertThrows(IllegalStateException.class, service::syncFromOracle);

        assertEquals(List.of("ORIGINAL"), jdbc.queryForList("SELECT code FROM sync_probe", String.class));
    }
}
