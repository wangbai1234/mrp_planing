#!/usr/bin/env python3
"""Start ONLY the isolated backend; retain logs and PID. No main runtime mutation."""
import os,pathlib,subprocess,secrets
root=pathlib.Path(__file__).resolve().parents[1]; repo=root.parents[1]
env=os.environ.copy()
values=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if line and not line.startswith('#'))
port=os.environ.get('MRP_TESTKIT_API_PORT','8180')
if port not in ('8180','8182'):
 raise SystemExit('Only isolated test-kit API ports 8180 and 8182 are allowed')
env.update(MRP_DB_URL='jdbc:mysql://127.0.0.1:3317/mrp_testkit?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8',MRP_DB_USERNAME='mrp_testkit',MRP_DB_PASSWORD=values['UAT_DB_PASSWORD'],MRP_REDIS_HOST='127.0.0.1',MRP_REDIS_PORT='6391',MRP_SERVER_PORT=port,MRP_FILE_UPLOAD_DIR=str(root/'runtime/uploads'),MRP_JWT_SECRET=secrets.token_hex(32),FEISHU_APP_ID='testkit-disabled',FEISHU_APP_SECRET='testkit-disabled',SPRING_APPLICATION_JSON='{"redisson":{"single-server-config":{"address":"redis://127.0.0.1:6391"}}}')
(root/'runtime/uploads').mkdir(parents=True,exist_ok=True)
log_path=root/'runtime'/('backend.log' if port=='8180' else f'backend-{port}.log')
pid_path=root/'runtime'/('backend.pid' if port=='8180' else f'backend-{port}.pid')
with log_path.open('ab') as log:
 p=subprocess.Popen(['./mvnw','spring-boot:run'],cwd=repo/'backend',env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
pid_path.write_text(str(p.pid))
print('Isolated backend launcher PID',p.pid,'port',port,'log',log_path)
