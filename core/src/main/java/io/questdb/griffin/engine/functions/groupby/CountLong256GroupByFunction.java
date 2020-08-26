/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
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

package io.questdb.griffin.engine.functions.groupby;

import io.questdb.cairo.ArrayColumnTypes;
import io.questdb.cairo.ColumnType;
import io.questdb.cairo.map.MapValue;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.Record;
import io.questdb.griffin.engine.functions.GroupByFunction;
import io.questdb.griffin.engine.functions.LongFunction;
import io.questdb.std.Long256;
import io.questdb.std.Long256HashSet;
import io.questdb.std.Numbers;
import io.questdb.std.ObjList;

public class CountLong256GroupByFunction extends LongFunction implements GroupByFunction {
    private final Function arg;
    private final ObjList<Long256HashSet> sets = new ObjList<>();
    private int valueIndex;
    private int setIndex;

    public CountLong256GroupByFunction(int position, Function arg) {
        super(position);
        this.arg = arg;
    }

    @Override
    public void computeFirst(MapValue mapValue, Record record) {
        final Long256HashSet set;
        if (sets.size() <= setIndex) {
            sets.extendAndSet(setIndex, set = new Long256HashSet());
        } else {
            set = sets.getQuick(setIndex);
        }

        set.clear();
        Long256 val = arg.getLong256A(record);
        set.add(val.getLong0(), val.getLong1(), val.getLong2(), val.getLong3());
        mapValue.putLong(valueIndex, 1L);
        mapValue.putInt(valueIndex + 1, setIndex++);
    }

    @Override
    public void computeNext(MapValue mapValue, Record record) {
        final Long256HashSet set = sets.getQuick(mapValue.getInt(valueIndex + 1));
        final Long256 val = arg.getLong256A(record);
        final int index = set.keyIndex(val.getLong0(), val.getLong1(), val.getLong2(), val.getLong3());
        if (index < 0) {
            return;
        }
        set.addAt(index, val.getLong0(), val.getLong1(), val.getLong2(), val.getLong3());
        mapValue.addLong(valueIndex, 1);
    }

    @Override
    public void pushValueTypes(ArrayColumnTypes columnTypes) {
        this.valueIndex = columnTypes.getColumnCount();
        columnTypes.add(ColumnType.LONG);
        columnTypes.add(ColumnType.INT);
    }

    @Override
    public void setLong(MapValue mapValue, long value) {
        mapValue.putLong(valueIndex, value);
    }

    @Override
    public void setNull(MapValue mapValue) {
        mapValue.putLong(valueIndex, Numbers.LONG_NaN);
    }

    @Override
    public long getLong(Record rec) {
        return rec.getLong(valueIndex);
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public void toTop() {
        setIndex = 0;
    }
}