-- V012: 初始化物料分类数据并回填物料分类ID
-- 数据来源：分类编码.xlsx（Sheet2: 二级分类，Sheet1: 料号→分类编码）
-- 幂等：使用 INSERT IGNORE 避免重复

-- 一级分类
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort) VALUES
('01', 'PCBA', NULL, 1, 1),
('02', '机头', NULL, 1, 2),
('03', '模具夹具', NULL, 1, 3),
('08', '物料', NULL, 1, 8),
('09', '整机', NULL, 1, 9);

-- 二级分类：01 PCBA
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '01001', '生产加工项目主板PCBA', id, 2, 1 FROM material_category WHERE code = '01' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '01002', '生产加工平台小板PCBA', id, 2, 2 FROM material_category WHERE code = '01' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '01003', '外购半成品组件', id, 2, 3 FROM material_category WHERE code = '01' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '01004', '项目主板PCBA', id, 2, 4 FROM material_category WHERE code = '01' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '01005', '平台非主板PCBA', id, 2, 5 FROM material_category WHERE code = '01' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '01008', '生产加工X合一', id, 2, 6 FROM material_category WHERE code = '01' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '019001', '太阳能板', id, 2, 7 FROM material_category WHERE code = '01' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '019101', '风扇', id, 2, 8 FROM material_category WHERE code = '01' AND is_deleted = 0;

-- 二级分类：02 机头
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '02001', '生产加工机头', id, 2, 1 FROM material_category WHERE code = '02' AND is_deleted = 0;

-- 二级分类：03 模具夹具
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '03001', '工具(夹具及配套件)', id, 2, 1 FROM material_category WHERE code = '03' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '03002', '模具', id, 2, 2 FROM material_category WHERE code = '03' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '03003', '模型机', id, 2, 3 FROM material_category WHERE code = '03' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '03004', '手板模型机', id, 2, 4 FROM material_category WHERE code = '03' AND is_deleted = 0;

-- 二级分类：08 物料
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08001', 'SAW/DualSaw', id, 2, 1 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08002', 'EMI+ESD器件', id, 2, 2 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08003', '混合电路滤波器_LPF', id, 2, 3 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08004', '混合电路滤波器_Balun', id, 2, 4 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08005', '0402普通电阻', id, 2, 5 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08006', '0603普通电阻', id, 2, 6 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08007', '0805普通电阻', id, 2, 7 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08008', '浪涌管/TVS管', id, 2, 8 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08009', 'Sensor', id, 2, 9 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08010', '定向耦合器/双定向耦合器', id, 2, 10 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08011', '混合电路滤波器_BPF', id, 2, 11 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08012', '0402贴片磁珠', id, 2, 12 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08013', '0603贴片磁珠', id, 2, 13 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08014', 'Duplexer/Quadplexer', id, 2, 14 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08015', '贴片马达', id, 2, 15 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08016', '硅MIC', id, 2, 16 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08017', 'G/M/Gyro-Sensor', id, 2, 17 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08018', '1206贴片磁珠', id, 2, 18 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08019', 'EMI+ESD器件_Common Mode', id, 2, 19 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08020', 'EMCP', id, 2, 20 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08021', 'LED灯驱动', id, 2, 21 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08022', 'DC-DC', id, 2, 22 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08023', 'PMIC', id, 2, 23 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08024', 'BB', id, 2, 24 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08025', 'RF', id, 2, 25 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08026', '接口电路/驱动电路', id, 2, 26 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08027', 'Breath LED Driver', id, 2, 27 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08028', 'OVP', id, 2, 28 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08029', '充电', id, 2, 29 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08030', 'Connectivity', id, 2, 30 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08031', 'AUDIO PA', id, 2, 31 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08032', '2G/3G/4G PA', id, 2, 32 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08033', 'TX-Module', id, 2, 33 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08034', '贴片发光二极管', id, 2, 34 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08035', 'Discrete RF', id, 2, 35 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08036', 'LNA', id, 2, 36 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08037', '有源晶振_VCTCXO/TCXO', id, 2, 37 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08038', '备份电池', id, 2, 38 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08039', 'LDO', id, 2, 39 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08040', '贴片三极管', id, 2, 40 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08041', '模拟开关', id, 2, 41 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08042', 'RF Switch_SPxT', id, 2, 42 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08043', '贴片MOS管', id, 2, 43 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08044', '无源晶体_Crystal+Thermistor', id, 2, 44 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08045', 'RF Switch-其他', id, 2, 45 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08046', 'RF Switch_DPxT', id, 2, 46 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08047', 'RF FEM', id, 2, 47 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08048', '贴片开关', id, 2, 48 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08049', 'NOR/SPI/NAND', id, 2, 49 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08050', 'Schottky二极管', id, 2, 50 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08051', 'DDR', id, 2, 51 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08052', 'Zener二极管', id, 2, 52 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08053', 'EMMC/UFS', id, 2, 53 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08054', '背光', id, 2, 54 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08055', '0201贴片叠层电感', id, 2, 55 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08056', '0402贴片叠层电感', id, 2, 56 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08057', '0402贴片线绕电感', id, 2, 57 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08058', '0603贴片线绕电感', id, 2, 58 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08059', '贴片功率电感', id, 2, 59 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08060', '0402片状电容', id, 2, 60 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08061', '无源晶体_Crystal', id, 2, 61 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08062', '0201普通电阻', id, 2, 62 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08063', '0201片状电容', id, 2, 63 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08064', '0603贴片叠层电感', id, 2, 64 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08065', '0805贴片叠层电感', id, 2, 65 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08066', '0805片状电容', id, 2, 66 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08067', '1206片状电容', id, 2, 67 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08068', '贴片钽电解电容(有机高分子电容)', id, 2, 68 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08069', '1206普通电阻', id, 2, 69 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08070', '排阻', id, 2, 70 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08071', '贴片热敏电阻', id, 2, 71 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08072', '插接式电解电容', id, 2, 72 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08073', '0603片状电容', id, 2, 73 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08074', '其他电阻', id, 2, 74 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08075', '屏蔽罩', id, 2, 75 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08076', '普通电池连接器', id, 2, 76 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08077', '板板连接器', id, 2, 77 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08078', '贴片弹片', id, 2, 78 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08079', '贴片垫片', id, 2, 79 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08080', '同轴线连接器', id, 2, 80 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08081', '射频测试座', id, 2, 81 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08082', 'SIM/SD卡座连接器', id, 2, 82 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08083', '耳机座连接器', id, 2, 83 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08084', 'USB连接器', id, 2, 84 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08085', 'FPC连接器', id, 2, 85 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08086', '不可拆电池连接器', id, 2, 86 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08087', '封箱胶带', id, 2, 87 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08088', '托盘及围套', id, 2, 88 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08089', '通用塑料袋/热缩膜', id, 2, 89 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08090', '标签', id, 2, 90 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08091', 'ECM MIC', id, 2, 91 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08092', '螺钉', id, 2, 92 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08093', '扁平马达', id, 2, 93 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08094', '柱状马达', id, 2, 94 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08095', '主板PCB', id, 2, 95 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08096', '同轴电缆', id, 2, 96 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08097', '非手机壳体组件', id, 2, 97 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08098', '缓冲压紧材料（泡棉/海绵/胶垫）', id, 2, 98 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08099', '指纹', id, 2, 99 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08100', '液晶显示模块', id, 2, 100 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08101', '卡托', id, 2, 101 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08102', '其它封装电感', id, 2, 102 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08103', '贴片电解电容', id, 2, 103 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08104', 'Diplexer', id, 2, 104 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08105', '密封遮蔽材料（麦拉等）', id, 2, 105 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08106', '支架', id, 2, 106 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08107', '胶套', id, 2, 107 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08108', '金属冲压/压铸件', id, 2, 108 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08109', '同轴线缆连接器', id, 2, 109 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08110', '售后PCBA包装', id, 2, 110 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08111', '导热材料', id, 2, 111 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08112', '喇叭BOX', id, 2, 112 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08113', '彩盒封套/袖套', id, 2, 113 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08114', '混合电路滤波器_HPF', id, 2, 114 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08115', '外购X合一', id, 2, 115 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08116', '背胶', id, 2, 116 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08117', 'WIFI/BT/GPS/WIMAX FEM', id, 2, 117 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08118', '编解码器_Codec', id, 2, 118 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08119', '卡通箱隔板/填充板', id, 2, 119 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08120', '结构虚拟件', id, 2, 120 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08121', 'FPC', id, 2, 121 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08122', '胶棒/硅脂/灌封胶/胶水', id, 2, 122 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08123', '手机充电器模块', id, 2, 123 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08124', '说明书/附页', id, 2, 124 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08125', '摄像头模块', id, 2, 125 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08126', 'HWE', id, 2, 126 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08127', '外箱', id, 2, 127 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08128', 'combo RF', id, 2, 128 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08129', '听筒', id, 2, 129 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08130', '1812片状电容', id, 2, 130 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08131', '小米', id, 2, 131 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08132', '编解码器_ISP', id, 2, 132 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08133', '电量计', id, 2, 133 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08134', '纸包装盒/彩盒', id, 2, 134 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08135', '后壳组件', id, 2, 135 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08136', '定制件', id, 2, 136 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08137', '挂绳/皮套/擦拭布/手写笔', id, 2, 137 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08138', '其他组件及包装（皮套、彩盒、电池、模型机等）', id, 2, 138 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08139', '头戴、面贴、魔术贴类', id, 2, 139 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08140', '转接FPC', id, 2, 140 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08141', '门电路', id, 2, 141 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08142', '数据线模块', id, 2, 142 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08143', '子板/上板PCB', id, 2, 143 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08144', '薄膜开关', id, 2, 144 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08145', '非圆形喇叭', id, 2, 145 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08146', '蛋格/吸塑/内托/纸护角/彩盒内卡', id, 2, 146 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08147', '前壳组件', id, 2, 147 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08148', '保护膜/广告膜', id, 2, 148 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08149', '镜片类', id, 2, 149 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08150', '导光、散光类', id, 2, 150 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08151', '泡沫/海绵/珍珠棉/缠绕膜等包装材料', id, 2, 151 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08152', '手机电池模块', id, 2, 152 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08153', '装配类线缆', id, 2, 153 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08154', '散热部件（散热片、铜管）', id, 2, 154 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08155', '屏蔽导电材料（导电布/导电海绵/导电胶）', id, 2, 155 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08157', '存储卡/转接卡', id, 2, 157 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08158', '天线类', id, 2, 158 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08159', '壳体单体', id, 2, 159 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08160', '防尘材料', id, 2, 160 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08161', '玻璃类', id, 2, 161 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08162', '弹簧', id, 2, 162 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08163', '胶纸/胶带/双面胶', id, 2, 163 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08164', '装饰件', id, 2, 164 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08165', '01005片状电容', id, 2, 165 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08166', '圆形喇叭', id, 2, 166 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08167', '电池盖组件', id, 2, 167 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08169', 'TP_LENS', id, 2, 169 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08170', '顶针连接器', id, 2, 170 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08171', '01005普通电阻', id, 2, 171 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08172', '模型机', id, 2, 172 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08173', '其他PCB', id, 2, 173 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08174', 'Filter（其它EMI滤波器件）', id, 2, 174 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08175', 'RF Power Management', id, 2, 175 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08176', '扎带/线扣及塑料辅件', id, 2, 176 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08177', '其他封装磁珠', id, 2, 177 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08178', '01005贴片叠层电感', id, 2, 178 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08179', '化工及工艺材料类（清洗剂/助焊剂/防氧化油/润滑油/酒精/洗洁剂/油漆/锡膏/锡线等）', id, 2, 179 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08180', '其他类电源芯片', id, 2, 180 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08181', 'Image-Sensor', id, 2, 181 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08182', '磁铁', id, 2, 182 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08183', '插件式安规电容', id, 2, 183 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08184', '0201贴片磁珠', id, 2, 184 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08185', '螺母', id, 2, 185 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08187', 'Filter（LC）', id, 2, 187 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08188', '光耦合器', id, 2, 188 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08189', 'HDMI连接器', id, 2, 189 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08190', 'DC JACK连接器', id, 2, 190 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08191', '取卡针', id, 2, 191 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08192', '防静电袋/铝薄袋/充气袋', id, 2, 192 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08193', '碳带', id, 2, 193 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08194', '卡塞/胶塞类', id, 2, 194 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08195', '干燥剂', id, 2, 195 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08196', '键盘板PCB', id, 2, 196 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08197', '售后X合一组件', id, 2, 197 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08198', 'UMCP', id, 2, 198 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08199', '电子虚拟件', id, 2, 199 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08200', 'RF Tuner', id, 2, 200 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08201', 'Triplexer', id, 2, 201 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08202', 'OCP', id, 2, 202 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08203', '防水标贴', id, 2, 203 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08206', '0805贴片磁珠', id, 2, 206 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08207', '镭雕工艺', id, 2, 207 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08208', '转轴类', id, 2, 208 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08209', '齿轮、丝杆、丝锥类', id, 2, 209 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08210', '标牌', id, 2, 210 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08211', '滑轨类', id, 2, 211 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08212', '耳机模块', id, 2, 212 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08213', 'OPPO', id, 2, 213 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08214', '三包凭证/保修卡', id, 2, 214 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08216', '加工费物料', id, 2, 216 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08217', '外购PCBA', id, 2, 217 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08218', '外购机头', id, 2, 218 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08219', '外购整机配件', id, 2, 219 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08220', '按键类', id, 2, 220 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08221', '数码管显示模块', id, 2, 221 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08222', 'PHY', id, 2, 222 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08223', '晶闸管', id, 2, 223 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08224', '肖特基整流桥', id, 2, 224 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08225', '国际SIM卡、说明书', id, 2, 225 FROM material_category WHERE code = '08' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '08226', 'SIM卡', id, 2, 226 FROM material_category WHERE code = '08' AND is_deleted = 0;

-- 二级分类：09 整机
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09001', '合装整机', id, 2, 1 FROM material_category WHERE code = '09' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09002', '外购整机', id, 2, 2 FROM material_category WHERE code = '09' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09003', '虚拟整机', id, 2, 3 FROM material_category WHERE code = '09' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09004', '其他组件及包装（皮套、彩盒、电池、模型机等）', id, 2, 4 FROM material_category WHERE code = '09' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09005', '售后X合一组件包装', id, 2, 5 FROM material_category WHERE code = '09' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09006', '售后PCBA包装', id, 2, 6 FROM material_category WHERE code = '09' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09007', '售后机头包装', id, 2, 7 FROM material_category WHERE code = '09' AND is_deleted = 0;
INSERT IGNORE INTO material_category (code, name, parent_id, level, sort)
SELECT '09008', '其他组装整机', id, 2, 8 FROM material_category WHERE code = '09' AND is_deleted = 0;

-- 回填 material.material_category_id
-- 通过 material 表的 category 字段（旧分类名）关联 material_category 的 code
-- 这里使用子查询匹配，因为旧 category 字段存储的是分类名称文本
UPDATE material m
    INNER JOIN material_category mc ON mc.code = (
        SELECT mc2.code FROM material_category mc2
        WHERE mc2.name = m.category AND mc2.is_deleted = 0
        LIMIT 1
    )
SET m.material_category_id = mc.id
WHERE m.is_deleted = 0
    AND m.category IS NOT NULL
    AND m.category != ''
    AND m.material_category_id IS NULL;
