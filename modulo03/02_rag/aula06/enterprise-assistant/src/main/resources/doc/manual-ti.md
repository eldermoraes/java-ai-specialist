# Manual de Infraestrutura de TI da Acme Corp - v3.2

## Seção 1 - Reset de Senha de Rede

Caso a sua senha da rede corporativa tenha expirado ou você tenha esquecido,
siga o procedimento abaixo para reset:

1. Acesse o portal de autoatendimento em https://senha.acmecorp.local pelo
   navegador corporativo.
2. Informe seu login de rede (formato: nome.sobrenome) e clique em
   "Esqueci minha senha".
3. Você receberá um código de 6 dígitos no seu e-mail corporativo. Esse
   código tem validade de 15 minutos.
4. Informe o código no portal e cadastre uma nova senha. A senha deve ter
   no mínimo 14 caracteres, com letras maiúsculas, minúsculas, números e
   ao menos um símbolo especial.
5. Após cadastrar a nova senha, faça logout e login novamente em todos os
   sistemas para que a credencial seja propagada.

Se você estiver fora da rede corporativa, conecte-se primeiro à VPN
(Seção 2) ou contate o helpdesk pelo ramal 4040.

## Seção 2 - Configuração da VPN

A VPN corporativa Acme usa o cliente OpenVPN. Procedimento:

1. Baixe o cliente OpenVPN Connect a partir do portal interno
   https://downloads.acmecorp.local/vpn.
2. Solicite o arquivo de configuração `.ovpn` ao seu gestor imediato.
3. Importe o arquivo no cliente e use suas credenciais de rede para
   autenticar.
4. A VPN exige autenticação multifator (MFA) — confira a Seção 3.
5. Em sistemas Linux, instale o pacote `openvpn` via gerenciador de
   pacotes da sua distribuição (apt, dnf, pacman) e use o comando
   `sudo openvpn --config arquivo.ovpn` para conectar.
6. Em macOS e Windows, basta abrir o cliente Tunnelblick ou OpenVPN GUI
   e clicar em "Conectar".

## Seção 3 - Política de MFA (Multi-Factor Authentication)

Todo acesso aos servidores de produção, ambiente de homologação e VPN
exige autenticação multifator. Aplicativos suportados:

- Microsoft Authenticator
- Google Authenticator
- Authy

Para registrar um novo dispositivo MFA:

1. Vá ao portal de autoatendimento em https://mfa.acmecorp.local.
2. Escaneie o QR Code com o aplicativo autenticador escolhido.
3. Confirme o código de 6 dígitos que aparecerá no app.
4. Salve os códigos de recuperação de emergência em local seguro.

A política exige re-registro de MFA a cada 12 meses.

## Seção 4 - Acesso a Servidores de Produção

O acesso aos servidores de produção é feito exclusivamente via bastion
host (jump server) `bastion.acmecorp.local` na porta 22. Requer chave SSH
registrada no portal de identidades e MFA ativo. Sessões inativas são
encerradas após 15 minutos.
