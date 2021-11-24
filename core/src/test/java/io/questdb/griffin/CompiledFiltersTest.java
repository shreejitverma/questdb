/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin;

import org.junit.Test;

public class CompiledFiltersTest extends AbstractGriffinTest {

    // TODO keep this test for advanced features such as random access cursor

    @Test
    public void testSelectAllTypesFromRecord() throws Exception {
        final String query = "select * from x where b = true and kk < 10";
        final String expected = "kk\ta\tb\tc\td\te\tf\tg\ti\tj\tk\tl\tm\tn\tcc\tl2\thash1b\thash2b\thash3b\thash1c\thash2c\thash4c\thash8c\n" +
                "2\t1637847416\ttrue\tV\t0.4900510449885239\t0.8258\t553\t2015-12-28T22:25:40.934Z\t\t-7611030538224290496\t1970-01-05T15:15:00.000000Z\t37\t00000000 3e e3 f1 f1 1e ca 9c 1d 06 ac\tKGHVUVSDOTSED\tY\t0xbccb30ed7795ebc85f20a35e80e154f458dfd08eeb9cc39ecec82869edec121b\t0\t10\t110\te\tsj\tfhcq\t35jvygt2\n" +
                "3\t844704299\ttrue\t\t0.3456897991538844\t0.2401\t775\t2015-08-03T15:58:03.335Z\tVTJW\t-8910603140262731534\t1970-01-05T15:23:20.000000Z\t24\t00000000 ac a8 3b a6 dc 3b 7d 2b e3 92 fe 69 38 e1 77 9a\n" +
                "00000010 e7 0c 89\tLJUMLGLHMLLEO\tY\t0x772c8b7f9505620ebbdfe8ff0cd60c64712fde5706d6ea2f545ded49c47eea61\t0\t01\t000\tf\t33\teusj\tb5z6npxr\n" +
                "6\t-1501720177\ttrue\tP\t0.18158967304439033\t0.8197\t501\t2015-06-08T17:20:46.703Z\tPEHN\t-4229502740666959541\t1970-01-05T15:48:20.000000Z\t19\t\tTNLEGP\tU\t0x0b4735986b97a80520051a2ed05467f71d3abd90d55b0a125db8f13ef95ce839\t1\t01\t010\tr\tc0\twhjh\trcqfw2hw\n" +
                "8\t526232578\ttrue\tE\t0.6379992093447574\t0.8515\t850\t2015-08-19T05:52:05.329Z\tPEHN\t-5157086556591926155\t1970-01-05T16:05:00.000000Z\t42\t00000000 6d 8c d8 ac c8 46 3b 47 3c e1 72 3b 9d\tJSMKIXEYVTUPD\tH\t0x5ec6d73428fb1c01b680be3ee552450eef8b1c47f7e7f9ecae395228bc24ce17\t0\t11\t000\t5\ttp\tx578\ttdnxkw6d\n";
        final String ddl = "create table x as (select" +
                " cast(x as int) kk," +
                " rnd_int() a," +
                " rnd_boolean() b," +
                " rnd_str(1,1,2) c," +
                " rnd_double(2) d," +
                " rnd_float(2) e," +
                " rnd_short(10,1024) f," +
                " rnd_date(to_date('2015', 'yyyy'), to_date('2016', 'yyyy'), 2) g," +
                " rnd_symbol(4,4,4,2) i," +
                " rnd_long() j," +
                " timestamp_sequence(400000000000, 500000000) k," +
                " rnd_byte(2,50) l," +
                " rnd_bin(10, 20, 2) m," +
                " rnd_str(5,16,2) n," +
                " rnd_char() cc," +
                " rnd_long256() l2," +
                " rnd_geohash(1) hash1b," +
                " rnd_geohash(2) hash2b," +
                " rnd_geohash(3) hash3b," +
                " rnd_geohash(5) hash1c," +
                " rnd_geohash(10) hash2c," +
                " rnd_geohash(20) hash4c," +
                " rnd_geohash(40) hash8c" +
                " from long_sequence(100)) timestamp(k) partition by DAY";

        assertQuery(expected,
                query,
                ddl,
                "k",
                false);
    }
}
