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

package io.questdb.cutlass.text.types;

import io.questdb.cairo.ColumnType;
import io.questdb.cairo.TableWriter;
import io.questdb.std.Numbers;
import io.questdb.std.str.DirectByteCharSequence;

public final class BadTimestampAdapter  extends TimestampAdapter {

    public static final BadTimestampAdapter INSTANCE = new BadTimestampAdapter();

    private BadTimestampAdapter() {
    }

    @Override
    public int getType() {
        return ColumnType.TIMESTAMP;
    }

    @Override
    public boolean probe(CharSequence text) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(TableWriter.Row row, int column, DirectByteCharSequence value) {
        row.putTimestamp(column, Numbers.LONG_NaN);
    }

    @Override
    public long getTimestamp(DirectByteCharSequence value) {
        return Numbers.LONG_NaN;
    }
}
