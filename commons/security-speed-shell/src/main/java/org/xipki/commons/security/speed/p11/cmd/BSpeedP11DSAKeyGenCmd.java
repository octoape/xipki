/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.commons.security.speed.p11.cmd;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.commons.common.LoadExecutor;
import org.xipki.commons.security.pkcs11.P11Slot;
import org.xipki.commons.security.speed.cmd.DSAControl;
import org.xipki.commons.security.speed.p11.P11DSAKeyGenLoadTest;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-tk", name = "bspeed-dsa-gen",
        description = "performance test of PKCS#11 DSA key generation (batch)")
@Service
// CHECKSTYLE:SKIP
public class BSpeedP11DSAKeyGenCmd extends BSpeedP11CommandSupport {

    private final BlockingDeque<DSAControl> queue = new LinkedBlockingDeque<>();

    public BSpeedP11DSAKeyGenCmd() {
        queue.add(new DSAControl(1024, 160));
        queue.add(new DSAControl(2048, 224));
        queue.add(new DSAControl(2048, 256));
        queue.add(new DSAControl(3072, 256));
    }

    @Override
    protected LoadExecutor nextTester()
    throws Exception {
        DSAControl control = queue.takeFirst();
        if (control == null) {
            return null;
        }

        P11Slot slot = getSlot();
        return new P11DSAKeyGenLoadTest(slot, control.getPlen(), control.getQlen());
    }

}
