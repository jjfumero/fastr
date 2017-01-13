/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.ArithmeticWhiteList;
import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_abs extends TestBase {

    @Test
    public void testabs1() {
        assertEval("argv <- list(c(0.9, 0.1, 0.3, 0.5, 0.7, 0.9, 0.1, 0.3, 0.5));abs(argv[[1]]);");
    }

    @Test
    public void testabs2() {
        assertEval("argv <- list(c(TRUE, TRUE, TRUE, TRUE));abs(argv[[1]]);");
    }

    @Test
    public void testabs3() {
        assertEval("argv <- list(c(-0.510763209393394, Inf, 1, 1, Inf, 1, 0, -1.95785440613009, -48.49854545454, -Inf, 1, 1, 0.342969776609699, 0.00707175387211123));abs(argv[[1]]);");
    }

    @Test
    public void testabs4() {
        assertEval(Ignored.Unknown,
                        "argv <- list(c(0, 0, 0, 0, 0, 1.75368801162502e-134, 0, 0, 0, 2.60477585273833e-251, 1.16485035372295e-260, 0, 1.53160350210786e-322, 0.333331382328728, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3.44161262707711e-123, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.968811545398e-173, 0, 8.2359965384697e-150, 0, 0, 0, 0, 6.51733217171341e-10, 0, 2.36840184577368e-67, 0, 9.4348408357524e-307, 0, 1.59959906013771e-89, 0, 8.73836857865034e-286, 7.09716190970992e-54, 0, 0, 0, 1.530425353017e-274, 8.57590058044551e-14, 0.333333106397154, 0, 0, 1.36895217898448e-199, 2.0226102635783e-177, 5.50445388209462e-42, 0, 0, 0, 0, 1.07846402051283e-44, 1.88605464411243e-186, 1.09156111051203e-26, 0, 3.0702877273237e-124, 0.333333209689785, 0, 0, 0, 0, 0, 0, 3.09816093866831e-94, 0, 0, 4.7522727332095e-272, 0, 0, 2.30093251441394e-06, 0, 0, 1.27082826644707e-274, 0, 0, 0, 0, 0, 0, 0, 4.5662025456054e-65, 0, 2.77995853978268e-149, 0, 0, 0));abs(argv[[1]]);");
    }

    @Test
    public void testabs5() {
        assertEval("argv <- list(structure(c(-7.0990260398094, -6.52913885777653, -3.11767063409183, -18.6913646342089), .Dim = c(4L, 1L)));abs(argv[[1]]);");
    }

    @Test
    public void testabs6() {
        assertEval("argv <- list(structure(c(0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10, -11, -12, -13, -14, -15, -16, -17, -18, -19, -20, -21, -22, -23, -24, -25, -26, -27, -28, -29, -30, -31, -32, -33, -34, -35, -36, -37, -38, -39, -40, -41, -42, -43, -44, -45, -46, -47, -48, -49, -50, -51, -52, -53, -54, -55, -56, -57, -58, -59, -60, -61, -62, -63, -64, -65, -66, -67, -68, -69, -70, -71, -72, -73, -74, -75, -76, -77, -78, -79, -80, -81, -82, -83, -84, -85, -86, -87, -88, -89, -90, -91, -92, -93, -94, -95, -96, -97, -98, -99, -100, -101, -102, -103, -104, -105, -106, -107, -108, -109, -110, -111, -112, -113, -114, -115, -116, -117, -118, -119, -120, -121, -122, -123, -124, -125, -126, -127, -128, -129, -130, -131, -132, -133, -134, -135, -136, -137, -138, -139, -140, -141, -142, -143, -144, -145, -146, -147, -148, -149), .Tsp = c(4, 153, 1), class = 'ts'));abs(argv[[1]]);");
    }

    @Test
    public void testabs7() {
        assertEval("argv <- list(structure(c(3.5527136788005e-15+2.4168586625265e-16i, 2.4980018054066e-16-2.28189378671807e-16i, 0-6.89814774385614e-17i, 0-1.77454768520688e-17i), .Dim = c(2L, 2L)));abs(argv[[1]]);");
    }

    @Test
    public void testabs8() {
        assertEval("argv <- list(1e+07);abs(argv[[1]]);");
    }

    @Test
    public void testabs9() {
        assertEval("argv <- list(structure(c(56.8666666666667, 52.8833333333333), .Dim = 2L, .Dimnames = structure(list(K = c('0', '1')), .Names = 'K')));abs(argv[[1]]);");
    }

    @Test
    public void testabs10() {
        assertEval("argv <- list(structure(c(-7.38958333333333, NA, 1.69041666666667, -16.5495833333333, 8.82041666666667, 8.84041666666667, -2.24958333333333, 9.15041666666667, 0.950416666666669, -12.5095833333333, NA, 8.86041666666667, 1.05041666666667, 3.80041666666667, 5.92041666666667, 16.1404166666667, 3.45041666666667, -32.1695833333333, 12.7504166666667, 1.18041666666667, -6.72958333333333, 14.4804166666667, 1.89041666666667, -37.9795833333333, -0.299583333333331, 2.49041666666667, 7.31041666666667, 0.66041666666667, 2.78041666666667, 3.89041666666667, 3.13041666666667, -6.08958333333333, -1.00958333333333, -1.07958333333333, 9.01041666666667, 7.84041666666667, 8.30041666666668, 9.36041666666667, -6.32958333333333, -47.3395833333333, 4.30041666666667, -2.11958333333333, -4.10958333333333, -2.29958333333333, 11.0004166666667, -1.96958333333333, 11.1804166666667, 2.55041666666667, -2.35958333333333, 7.80041666666667), .Names = c('Alabama', 'Alaska', 'Arizona', 'Arkansas', 'California', 'Colorado', 'Connecticut', 'Delaware', 'Florida', 'Georgia', 'Hawaii', 'Idaho', 'Illinois', 'Indiana', 'Iowa', 'Kansas', 'Kentucky', 'Louisiana', 'Maine', 'Maryland', 'Massachusetts', 'Michigan', 'Minnesota', 'Mississippi', 'Missouri', 'Montana', 'Nebraska', 'Nevada', 'New Hampshire', 'New Jersey', 'New Mexico', 'New York', 'North Carolina', 'North Dakota', 'Ohio', 'Oklahoma', 'Oregon', 'Pennsylvania', 'Rhode Island', 'South Carolina', 'South Dakota', 'Tennessee', 'Texas', 'Utah', 'Vermont', 'Virginia', 'Washington', 'West Virginia', 'Wisconsin', 'Wyoming')));abs(argv[[1]]);");
    }

    @Test
    public void testabs11() {
        assertEval("argv <- list(c(NA, 1L));abs(argv[[1]]);");
    }

    @Test
    public void testabs12() {
        assertEval("argv <- list(structure(c(1.47191076131574, 0.586694550701453, NA, 0.258706725324317, 0.948371836939988, 0.396080061109718, NA, 0.350912037541581), .Dim = c(4L, 2L), .Dimnames = list(c('(Intercept)', 'x1', 'x2', 'x3'), c('Estimate', 'Std. Error'))));abs(argv[[1]]);");
    }

    @Test
    public void testabs13() {
        assertEval("argv <- list(structure(c(0.839669286317987, -1.37400428670216, 0.22900071445036, 0.305334285933814, 0.34350107167554, -0.0763335714834533, -0.877836072059713, 0.610668571867626, 0.763335714834533, -0.763335714834533, -0.763335714834533, 0.763335714834533, -0.45800142890072, 0.80150250057626, -0.22900071445036, -0.11450035722518, -0.34350107167554, 1.03050321502662, -1.03050321502662, 0.34350107167554, 0.11450035722518, -0.152667142966907, -0.0381667857417267, 0.0763335714834533, 0.496168214642447, -0.725168929092807, -0.0381667857417268, 0.267167500192087, 0.954169643543167, -1.52667142966907, 0.190833928708633, 0.381667857417267, 0.610668571867627, -2.78617535914605, 3.74034500268921, -1.56483821541079, 0.190833928708633, -0.572501786125899, 0.5725017861259, -0.190833928708634, 0.267167500192087, -0.229000714450361, -0.34350107167554, 0.305334285933814, 0.190833928708632, 0.190833928708634, -0.954169643543166, 0.572501786125899, -1.06867000076835, 1.67933857263597, -0.152667142966906, -0.45800142890072, -0.610668571867627, 0.877836072059714, 0.0763335714834535, -0.34350107167554, 0.381667857417267, -0.190833928708634, -0.763335714834533, 0.5725017861259, 0.496168214642447, -0.725168929092807, -0.0381667857417268, 0.267167500192087, 0.5725017861259, -0.763335714834533, -0.190833928708633, 0.381667857417267, 0.305334285933813, -0.534335000384173, 0.152667142966907, 0.076333571483453, -0.534335000384172, 0.839669286317985, -0.0763335714834534, -0.22900071445036, 0.0381667857417273, 0.0763335714834524, -0.267167500192087, 0.152667142966907, -0.22900071445036, 0.496168214642446, -0.305334285933813, 0.0381667857417267, 0, 0.190833928708633, -0.381667857417267, 0.190833928708633, 0.11450035722518, 0.0381667857417268, -0.419834643158993, 0.267167500192087, 0.11450035722518, -0.152667142966907, -0.0381667857417267, 0.0763335714834533, -0.22900071445036, 0.11450035722518, 0.45800142890072, -0.34350107167554, -0.496168214642447, 0.725168929092807, 0.0381667857417269, -0.267167500192087, 0.11450035722518, -0.534335000384173, 0.725168929092806, -0.305334285933813), .Names = c('M01', 'M01', 'M01', 'M01', 'M02', 'M02', 'M02', 'M02', 'M03', 'M03', 'M03', 'M03', 'M04', 'M04', 'M04', 'M04', 'M05', 'M05', 'M05', 'M05', 'M06', 'M06', 'M06', 'M06', 'M07', 'M07', 'M07', 'M07', 'M08', 'M08', 'M08', 'M08', 'M09', 'M09', 'M09', 'M09', 'M10', 'M10', 'M10', 'M10', 'M11', 'M11', 'M11', 'M11', 'M12', 'M12', 'M12', 'M12', 'M13', 'M13', 'M13', 'M13', 'M14', 'M14', 'M14', 'M14', 'M15', 'M15', 'M15', 'M15', 'M16', 'M16', 'M16', 'M16', 'F01', 'F01', 'F01', 'F01', 'F02', 'F02', 'F02', 'F02', 'F03', 'F03', 'F03', 'F03', 'F04', 'F04', 'F04', 'F04', 'F05', 'F05', 'F05', 'F05', 'F06', 'F06', 'F06', 'F06', 'F07', 'F07', 'F07', 'F07', 'F08', 'F08', 'F08', 'F08', 'F09', 'F09', 'F09', 'F09', 'F10', 'F10', 'F10', 'F10', 'F11', 'F11', 'F11', 'F11'), label = 'Standardized residuals'));abs(argv[[1]]);");
    }

    @Test
    public void testabs14() {
        assertEval("argv <- list(numeric(0));abs(argv[[1]]);");
    }

    @Test
    public void testabs15() {
        assertEval("argv <- list(structure(c(NA, NA), .Dim = 1:2, .Dimnames = list('x', c('Estimate', 'Std. Error'))));abs(argv[[1]]);");
    }

    @Test
    public void testabs16() {
        assertEval("argv <- list(-3.31827701955945e-05);abs(argv[[1]]);");
    }

    @Test
    public void testabs17() {
        assertEval("argv <- list(structure(c(-1.36919169254062, -0.210726311672344, 0.00840470379579385, 0.0843659249699204, 0.552921941721332), .Names = c('0%', '25%', '50%', '75%', '100%')));abs(argv[[1]]);");
    }

    @Test
    public void testabs18() {
        assertEval("argv <- list(structure(c(8, 7, 6, 5, 4, 3, 2, 1, 0, -1), .Tsp = c(2, 11, 1), class = 'ts'));abs(argv[[1]]);");
    }

    @Test
    public void testabs19() {
        assertEval("argv <- list(-32L);abs(argv[[1]]);");
    }

    @Test
    public void testabs20() {
        assertEval("argv <- list(structure(c(-0.233156370250776, -0.239412071306507, -0.467754722340573, -0.313136244157115, 0.165043251865522, -0.32096970624939, -0.383377620527381, -0.709110442848008, -0.235393959061767, 0.141653176746209, -0.522836600482894, -0.313130619764979, -0.127866584288678, -0.443661331439424, -0.272181655694344, -0.267312459146771, -0.421687966603442, -0.489456667260012, -1.09136324227316, -0.365563936224476, -0.19497185571599, -0.355887223690607, -0.284861760091765, 0.10349461735987, -0.29203835454261, -0.437137381511441, -0.283258760238879, -0.864706923796918, -0.28069027865338, -0.328236303967812, -0.413280526174513, 0.353631921283964, -0.170574581087077, -0.350164611041975, -0.35509309393052, 0.371846787851152, 0.0662214922636754, -0.38267166577059, -0.76433272993872, 0.337925286731474, -0.316383144846009, 0.872120012008955, -0.1910867580222, -0.27268759462975, 0.561668608501795, -0.414426404256215, 0.306241460697781, -0.475011430441313, -0.18104902231566, -0.313137168940244, -0.162511371099967, -0.332715804582844, -0.264583655672922, -0.27403739912759, 0.292926038918646, -0.0266469304789678, NaN, 0.246014195648949, -0.384306495215891, -0.0754669363487819, -0.19187230661589, -0.28069027865338, -0.310267174936681, -0.218229559122572, -0.132431123435626, -0.632568727580371, -0.025524040198577, -0.208280705060531, -0.339307274267833, -0.267312459146771, -0.38337762052738, -0.527937499202165, -0.0708139839175417, -0.249126729136488, -0.443661331439424, -0.282353670058315, -0.383117955201771, 0.465652476582142, -0.257667111448151, -0.923882106624253, -0.527902200672803, -0.106397317438703, -0.882784314083567, -0.171789859931029, -0.134719406450945, -0.334299917870866, -0.59592895672967, 0.0477670768238039, -1.67777307729147, 0.0145330024207598, 0.465392111094536, -0.188401359782389, -1.67777307729147), .Names = c('1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23', '24', '25', '26', '27', '28', '29', '30', '31', '32', '33', '34', '35', '36', '37', '38', '39', '40', '41', '42', '43', '44', '45', '46', '47', '48', '49', '50', '51', '52', '53', '54', '55', '56', '57', '58', '59', '60', '61', '62', '63', '64', '65', '66', '67', '68', '69', '70', '71', '72', '73', '74', '75', '76', '77', '78', '79', '80', '81', '82', '83', '84', '85', '86', '87', '88', '89', '90', '91', '92', '93')));abs(argv[[1]]);");
    }

    @Test
    public void testabs21() {
        assertEval("argv <- list(structure(c(2671, 6.026e+77, 3.161e+152, 3.501e+299, 2.409e+227, 1.529e+302), .Names = c('Min.', '1st Qu.', 'Median', 'Mean', '3rd Qu.', 'Max.')));abs(argv[[1]]);");
    }

    @Test
    public void testAbs() {
        assertEval("{ abs(0) }");
        assertEval("{ abs(100) }");
        assertEval("{ abs(-100) }");
        assertEval("{ abs(0/0) }");
        assertEval("{ abs((1:2)[3]) }");
        assertEval("{ abs((1/0)*(1-0i)) }");
        assertEval("{ abs(1) }");
        assertEval("{ abs(1L) }");
        assertEval("{ abs(-1) }");
        assertEval("{ abs(-1L) }");
        assertEval("{ abs(NA) }");
        assertEval("{ abs(c(1, 2, 3)) }");
        assertEval("{ abs(c(1L, 2L, 3L)) }");
        assertEval("{ abs(c(1, -2, 3)) }");
        assertEval("{ abs(c(1L, -2L, 3L)) }");
        assertEval("{ abs(c(1L, -2L, NA)) }");
        assertEval(ArithmeticWhiteList.WHITELIST, "{ abs((-1-0i)/(0+0i)) }");
        assertEval(ArithmeticWhiteList.WHITELIST, "{ abs((-0-1i)/(0+0i)) }");
        assertEval("{ abs(NA+0.1) }");
        assertEval("{ abs((0+0i)/0) }");
        assertEval("{ abs(c(1, -2, NA)) }");
        assertEval("{ abs(NULL) }");

        assertEval("{ abs(c(0/0,1i)) }");
        assertEval("{ abs(1:3) }");
        assertEval("{ abs(-1:-3) }");

        assertEval("{ is.integer(abs(FALSE)) }");
    }
}
